#!/usr/bin/env python3
"""Generate disposable PNG/video fixtures for UpdateChecks; uses stdlib and FFmpeg."""
import argparse, pathlib, struct, subprocess, zlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--output',default=str(ROOT/'app/build/update-fixtures'))
args=parser.parse_args();output=pathlib.Path(args.output).resolve();output.mkdir(parents=True,exist_ok=True)
def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
def png(color):
    raw=bytearray()
    for y in range(768):
        raw.append(0)
        for x in range(512):raw.extend((255,255,255) if (x//32+y//32)%2==0 and x%32<16 and y%32<16 else color)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!IIBBBBB',512,768,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
for name,color in [('Paisagem-A.png',(170,105,205)),('Paisagem-B.png',(65,145,205)),('Paisagem-C.png',(190,115,80)),('Screenshot_2026-10-05-12-00-00_com.android.settings.png',(45,100,205))]:
    (output/name).write_bytes(png(color))
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-stream_loop','-1','-i',str(ROOT/'tests/fixtures/EditorQA.mp4'),'-t','24','-c','copy',str(output/'MovimentoQA.mp4')],check=True)
print(output)
