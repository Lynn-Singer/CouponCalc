#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
消费券计算器 - APK 构建脚本
链路: aapt2 compile/link -> javac -> d8 -> zip(classes.dex) -> zipalign -> apksigner
不依赖 Gradle / 网络（全部使用本机已安装的 Android SDK build-tools）
"""
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find_sdk():
    """按 ANDROID_SDK / ANDROID_HOME / ANDROID_SDK_ROOT 及常见安装位置依次查找。"""
    for key in ("ANDROID_SDK", "ANDROID_HOME", "ANDROID_SDK_ROOT"):
        v = os.environ.get(key)
        if v and os.path.isdir(v):
            return v
    home = os.path.expanduser("~")
    candidates = [
        os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"),
        os.path.join(home, "AppData", "Local", "Android", "Sdk"),
        os.path.join(home, "Android", "Sdk"),
        os.path.join(home, "Library", "Android", "sdk"),   # macOS
        os.path.join(home, "Android", "Sdk"),              # Linux
    ]
    for c in candidates:
        if c and os.path.isdir(c):
            return c
    raise SystemExit(
        "找不到 Android SDK。请设置环境变量 ANDROID_SDK 指向 SDK 根目录，"
        "例如: set ANDROID_SDK=D:\\Android\\Sdk")


def newest_dir(parent):
    """取 parent 下版本号最大的子目录（build-tools/36.0.0、platforms/android-37.0 这类）。"""
    if not os.path.isdir(parent):
        raise SystemExit("缺少 SDK 子目录: " + parent)
    import re

    def key(name):
        return [int(x) for x in re.findall(r"\d+", name)] or [0]

    names = [n for n in os.listdir(parent)
             if os.path.isdir(os.path.join(parent, n))]
    if not names:
        raise SystemExit("SDK 子目录为空: " + parent)
    return max(names, key=key)


SDK = find_sdk()
BT = os.path.join(SDK, "build-tools", newest_dir(os.path.join(SDK, "build-tools")))
PLATFORM = os.path.join(SDK, "platforms",
                        newest_dir(os.path.join(SDK, "platforms")), "android.jar")

APP = os.path.join(ROOT, "app")
BUILD = os.path.join(ROOT, "build")
OUT = os.path.join(ROOT, "out")
KS = os.path.join(ROOT, ".keystore", "debug.keystore")

VERSION = "1.4.0"
APK_NAME = "CouponCalc-v%s.apk" % VERSION


def log(msg):
    print("[build] " + msg, flush=True)


def run(cmd, cwd=None):
    log("$ " + " ".join(os.path.basename(c) if i == 0 else c for i, c in enumerate(cmd)))
    p = subprocess.run(cmd, cwd=cwd)
    if p.returncode != 0:
        raise SystemExit("命令失败 (exit %d): %s" % (p.returncode, " ".join(cmd)))


def exe(name):
    return os.path.join(BT, name)


def main():
    for path in (BT, PLATFORM):
        if not os.path.exists(path):
            raise SystemExit("缺少构建环境: " + path)

    # 只清这一轮会重新生成的产物，不整目录删（避免大批量删除）
    stale = ["res.zip", "base.apk", "unsigned.apk", "aligned.apk", "sources.txt"]
    for name in stale:
        p = os.path.join(BUILD, name)
        if os.path.isfile(p):
            os.remove(p)
    for sub in ("gen", "dex"):
        d = os.path.join(BUILD, sub)
        if os.path.isdir(d):
            for n in os.listdir(d):
                f = os.path.join(d, n)
                if os.path.isfile(f):
                    os.remove(f)
    os.makedirs(os.path.join(BUILD, "gen"), exist_ok=True)
    os.makedirs(os.path.join(BUILD, "classes"), exist_ok=True)
    os.makedirs(os.path.join(BUILD, "dex"), exist_ok=True)
    os.makedirs(OUT, exist_ok=True)

    # 1. 资源编译
    log("1/6 编译资源")
    run([exe("aapt2.exe"), "compile", "--dir", os.path.join(APP, "res"),
         "-o", os.path.join(BUILD, "res.zip")])

    # 2. 资源链接 + 生成 R.java
    log("2/6 链接资源 & 生成 R.java")
    run([exe("aapt2.exe"), "link",
         "-o", os.path.join(BUILD, "base.apk"),
         "-I", PLATFORM,
         "--manifest", os.path.join(APP, "AndroidManifest.xml"),
         "-R", os.path.join(BUILD, "res.zip"),
         "--java", os.path.join(BUILD, "gen"),
         "--auto-add-overlay"])

    # 3. 编译 Java
    log("3/6 javac 编译")
    sources = []
    for root in (os.path.join(APP, "java"), os.path.join(BUILD, "gen")):
        for dirpath, _, files in os.walk(root):
            for f in files:
                if f.endswith(".java"):
                    sources.append(os.path.join(dirpath, f))
    argfile = os.path.join(BUILD, "sources.txt")
    with open(argfile, "w", encoding="utf-8") as fp:
        fp.writelines('"%s"\n' % s.replace("\\", "/") for s in sources)
    run(["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-nowarn", "-bootclasspath", PLATFORM, "-classpath", PLATFORM,
         "-d", os.path.join(BUILD, "classes"), "@" + argfile])

    # 4. 转 dex
    log("4/6 d8 转换 dex")
    classes = []
    for dirpath, _, files in os.walk(os.path.join(BUILD, "classes")):
        for f in files:
            if f.endswith(".class"):
                classes.append(os.path.join(dirpath, f))
    run(["cmd", "/c", exe("d8.bat"), "--lib", PLATFORM, "--min-api", "21",
         "--output", os.path.join(BUILD, "dex")] + classes)

    # 5. 合入 classes.dex
    log("5/6 打包 dex 进 apk")
    unsigned = os.path.join(BUILD, "unsigned.apk")
    shutil.copyfile(os.path.join(BUILD, "base.apk"), unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(BUILD, "dex", "classes.dex"), "classes.dex")

    # 对齐
    aligned = os.path.join(BUILD, "aligned.apk")
    run([exe("zipalign.exe"), "-f", "-p", "4", unsigned, aligned])

    # 6. 签名
    log("6/6 签名")
    if not os.path.exists(KS):
        os.makedirs(os.path.dirname(KS), exist_ok=True)
        run(["keytool", "-genkeypair", "-keystore", KS, "-alias", "couponcalc",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
             "-storepass", "android", "-keypass", "android",
             "-dname", "CN=CouponCalc, OU=Dev, O=Personal, L=Jinan, ST=Shandong, C=CN"])

    final = os.path.join(OUT, APK_NAME)
    if os.path.exists(final):
        os.remove(final)
    run(["cmd", "/c", exe("apksigner.bat"), "sign",
         "--ks", KS, "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--out", final, aligned])
    run(["cmd", "/c", exe("apksigner.bat"), "verify", "--print-certs", final])

    size = os.path.getsize(final) / 1024.0
    log("完成: %s (%.1f KB)" % (final, size))


if __name__ == "__main__":
    sys.exit(main())
