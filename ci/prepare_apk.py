#!/usr/bin/env python3
"""Copia e valida superficialmente o APK produzido pelo Gradle para dist/."""
import pathlib
import re
import shutil
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
match = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", gradle)
if not match:
    raise SystemExit('Nao encontrei versionName em app/build.gradle.')
version = match.group(1)

source = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
if not source.is_file() or source.stat().st_size == 0:
    raise SystemExit('Nao encontrei um APK compilado: ' + str(source))
with zipfile.ZipFile(source) as apk:
    if apk.testzip() is not None:
        raise SystemExit('APK ZIP corrompido.')
    contents = set(apk.namelist())
    if not {'AndroidManifest.xml', 'classes.dex'}.issubset(contents):
        raise SystemExit('APK sem AndroidManifest.xml ou classes.dex.')

outdir = ROOT / 'dist'
outdir.mkdir(exist_ok=True)
outfile = outdir / f'Galeria-{version}-debug.apk'
shutil.copy2(source, outfile)
print(f'APK preparado: {outfile} ({outfile.stat().st_size:,} bytes)')
