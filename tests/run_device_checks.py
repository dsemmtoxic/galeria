#!/usr/bin/env python3
"""Compile and run integration checks on a disposable Android 11+ emulator.

Requires a built and installed Galeria APK, media permissions and a video
longer than 15 seconds. Do not run against a phone containing personal media.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument("--sdk",required=True)
parser.add_argument("--ecj")
parser.add_argument("--serial",default="emulator-5556")
parser.add_argument("--class-name",default="DeviceChecks")
parser.add_argument("--compile-only",action="store_true")
args=parser.parse_args()
sdk=Path(args.sdk);tools=sdk/"build-tools/35.0.0";android=sdk/"platforms/android-35/android.jar"
build=ROOT/"app/build/device-checks"/args.class_name
build.mkdir(parents=True,exist_ok=True)
for d in ("classes","dex"):(build/d).mkdir(exist_ok=True)
manifest=build/"AndroidManifest.xml"
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.galeria.checks">
<uses-sdk android:minSdkVersion="30" android:targetSdkVersion="35"/>
<application android:label="Galeria Device Checks" android:debuggable="true"/>
<instrumentation android:name="app.galeria.CLASS_NAME" android:targetPackage="app.galeria" android:functionalTest="true"/>
</manifest>'''.replace("CLASS_NAME",args.class_name))
def run(command):
    subprocess.run([str(x) for x in command],check=True,stdin=subprocess.DEVNULL,cwd=ROOT)
run([tools/"aapt2","link","-I",android,"--manifest",manifest,"-o",build/"resources.apk"])
compiler=["java","-jar",args.ecj,"-source","1.8","-target","1.8","-warn:none"] if args.ecj else ["javac","-source","8","-target","8"]
run(compiler+["-encoding","UTF-8","-bootclasspath",str(android)+os.pathsep+str(tools/"core-lambda-stubs.jar"),
              "-classpath",ROOT/"app/build/native/classes","-d",build/"classes",ROOT/("tests/"+args.class_name+".java")])
run(["java","-cp",tools/"lib/d8.jar","com.android.tools.r8.D8","--release","--min-api","30","--lib",android,
     "--classpath",ROOT/"app/build/native/classes","--output",build/"dex"]+sorted((build/"classes").rglob("*.class")))
shutil.copyfile(build/"resources.apk",build/"unsigned.apk")
with zipfile.ZipFile(build/"unsigned.apk","a",compression=zipfile.ZIP_DEFLATED) as z:z.write(build/"dex/classes.dex","classes.dex")
run([tools/"zipalign","-f","4",build/"unsigned.apk",build/"aligned.apk"])
run(["java","-jar",tools/"lib/apksigner.jar","sign","--ks",ROOT/"signing/galeria-teste.jks",
     "--ks-pass","pass:galeria-teste-2026","--ks-key-alias","galeria","--out",build/"checks.apk",build/"aligned.apk"])
if args.compile_only:
    print("Compiled: "+str(build/"checks.apk"));raise SystemExit(0)
adb=sdk/"platform-tools/adb";base=[adb,"-s",args.serial]
run(base+["install","--no-incremental","-r",build/"checks.apk"])
process=subprocess.Popen([str(x) for x in base+["shell","am","instrument","-w","app.galeria.checks/app.galeria."+args.class_name]],
                         stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,cwd=ROOT)
lines=[]
for line in iter(process.stdout.readline,b""):
    decoded=line.decode(errors="replace");lines.append(decoded);print(decoded,end="",flush=True)
code=process.wait();text="".join(lines);(build/"results.txt").write_text(text)
if code or "PASS:" not in text or "FAIL:" in text:raise SystemExit(1)
