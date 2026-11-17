#!/usr/bin/env python3
"""Build the dependency-free Android application with an installed Android SDK.

Normally use ./gradlew assembleDebug in Android Studio's JDK.
This alternative supports an ECJ compiler when only a Java runtime is available.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
PASSWORD = "galeria-teste-2026"

def run(args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    parser.add_argument("--ecj", help="Optional path to ecj.jar")
    parser.add_argument("--output", default=str(ROOT / "app/build/outputs/apk/native/Galeria-1.3.6-teste.apk"))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Specify --sdk or ANDROID_HOME.")
    sdk = Path(args.sdk).resolve()
    tools = sdk / "build-tools/35.0.0"
    android = sdk / "platforms/android-35/android.jar"
    for needed in (tools / "aapt2", tools / "lib/d8.jar", android):
        if not needed.is_file():
            parser.error(f"Missing SDK component: {needed}")
    build = ROOT / "app/build/native"
    if build.exists():
        shutil.rmtree(build)
    for directory in ("generated", "classes", "dex"):
        (build / directory).mkdir(parents=True)
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    manifest.getroot().set("package", "app.galeria")
    manifest.write(build / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    run([tools / "aapt2", "compile", "--dir", ROOT / "app/src/main/res", "-o", build / "res.zip"])
    run([tools / "aapt2", "link", "-I", android, "--manifest", build / "AndroidManifest.xml",
         "--min-sdk-version", "26", "--target-sdk-version", "35", "--version-code", "20",
         "--version-name", "1.3.6", "--auto-add-overlay", "--java", build / "generated",
         "--custom-package", "app.galeria", "-o", build / "resources.apk", build / "res.zip"])
    sources = sorted((ROOT / "app/src/main/java").rglob("*.java")) + sorted((build / "generated").rglob("*.java"))
    if args.ecj:
        compiler = ["java", "-jar", str(Path(args.ecj).resolve()), "-source", "1.8", "-target", "1.8", "-warn:none"]
    else:
        compiler = ["javac", "-source", "8", "-target", "8", "-Xlint:-options"]
    boot = str(android)
    lambdas = tools / "core-lambda-stubs.jar"
    if lambdas.exists():
        boot += os.pathsep + str(lambdas)
    run(compiler + ["-encoding", "UTF-8", "-bootclasspath", boot, "-d", build / "classes"] + sources)
    classes = sorted((build / "classes").rglob("*.class"))
    run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "26",
         "--lib", android, "--output", build / "dex"] + classes)
    unsigned = build / "unsigned.apk"
    shutil.copyfile(build / "resources.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted((build / "dex").glob("*.dex")):
            apk.write(dex, dex.name)
    aligned = build / "aligned.apk"
    run([tools / "zipalign", "-p", "-f", "4", unsigned, aligned])
    key = ROOT / "signing/galeria-teste.jks"
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run(["keytool", "-genkeypair", "-keystore", key, "-alias", "galeria", "-storepass", PASSWORD,
             "-keypass", PASSWORD, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
             "-dname", "CN=Galeria Teste, OU=Desenvolvimento, O=Galeria, C=BR"])
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    run(["java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-key-alias", "galeria",
         "--ks-pass", f"pass:{PASSWORD}", "--key-pass", f"pass:{PASSWORD}", "--out", output, aligned])
    run(["java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", output])
    run([tools / "zipalign", "-c", "-p", "4", output])
    print(f"APK: {output}")

if __name__ == "__main__":
    main()
