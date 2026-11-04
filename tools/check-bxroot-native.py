#!/usr/bin/env python3
"""核对 bxroot 三库及两件原生调用入口的字节与 ELF；不替代 Android 运行验收。"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAMES = {"libbxroot.so", "libbxroot-runtime.so", "libbxroot-ulx.so"}
# 原生调用入口：bx-host（无 libc 静态 ELF）与回入口（PT_INTERP=/system/bin/linker64）。
NATIVE_ENTRIES = {"libbxroot-host.so", "libbxroot-enter.so"}
ALL = NAMES | NATIVE_ENTRIES
ANDROID_LINKER = b"/system/bin/linker64\0"
PREFIX = "lib/arm64-v8a/"


def verify(name, data, expected):
    if len(data) != expected["bytes"] or hashlib.sha256(data).hexdigest() != expected["sha256"]:
        raise ValueError(f"{name}: byte identity mismatch")
    if len(data) < 64 or data[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError(f"{name}: not little-endian ELF64")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", data)
    if header[1] != expected["elfType"] or header[2] != 183:
        raise ValueError(f"{name}: unexpected ELF type or architecture")
    phoff, entry_size, count = header[5], header[9], header[10]
    if entry_size != 56 or not 0 < count < 128 or phoff + count * entry_size > len(data):
        raise ValueError(f"{name}: invalid program headers")
    loads = 0
    interp_ok = False
    for i in range(count):
        kind, flags, offset, virtual, physical, size, memory, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, phoff + i * entry_size)
        if offset + size > len(data):
            raise ValueError(f"{name}: segment exceeds file")
        if kind == 1:
            loads += 1
            if size > memory or alignment < 16384 or offset % 16384 != virtual % 16384:
                raise ValueError(f"{name}: invalid or non-16KiB-compatible LOAD segment")
        if kind == 3 and name == "libbxroot-enter.so":
            if data[offset:offset + size] != ANDROID_LINKER:
                raise ValueError(f"{name}: native re-entry must use the Android linker")
            interp_ok = True
        elif kind == 3 and name != "libbxroot-runtime.so":
            raise ValueError(f"{name}: standalone launcher unexpectedly needs PT_INTERP")
    if not loads:
        raise ValueError(f"{name}: missing LOAD segments")
    if name == "libbxroot-enter.so" and not interp_ok:
        raise ValueError(f"{name}: native re-entry lacks PT_INTERP")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="同时从最终 APK 读取三库并逐字节核对")
    args = parser.parse_args()
    lock = json.loads((ROOT / "tools/bxroot-native.lock.json").read_text(encoding="utf-8"))
    expected = lock["libraries"]
    if lock["schemaVersion"] != 1 or set(expected) != ALL:
        raise ValueError("invalid bxroot lock")
    directory = ROOT / "app/src/main/jniLibs/arm64-v8a"
    if {p.name for p in directory.glob("libbxroot*.so")} != ALL:
        raise ValueError("bxroot requires exactly the three libraries plus two native entries")
    for name in sorted(ALL):
        verify(name, (directory / name).read_bytes(), expected[name])
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            entries = [n for n in archive.namelist() if n.startswith("lib/")
                       and Path(n).name.startswith("libbxroot")]
            if len(entries) != len(ALL) or set(entries) != {PREFIX + n for n in ALL}:
                raise ValueError("unexpected bxroot APK library layout or duplicate entry")
            for name in sorted(ALL):
                verify(name, archive.read(PREFIX + name), expected[name])
    print("bxroot: 3 exact AArch64 libraries + 2 native-call entries verified; "
          "ELF LOAD compatible with 16KiB pages")
    print("Android execution acceptance: not established by this byte/layout check")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, struct.error, zipfile.BadZipFile) as failure:
        print(f"BXROOT_NATIVE_CHECK_FAILED: {failure}", file=sys.stderr)
        sys.exit(1)
