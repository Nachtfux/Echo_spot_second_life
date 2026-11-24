"""Download a spread-out subset of openWakeWord's ACAV100M negative features (~4 GB of 16.5 GB).

    python3 fetch_features.py OUT.npy [GB=4] [PHASE=0]

The .npy is fetched with HTTP Range requests in CHUNKS evenly spaced over the file and written
as a new, valid .npy with the reduced row count. PHASE 0.5 takes the chunks halfway between
those of PHASE 0, so two runs give two disjoint subsets. Stdlib only (runs with any python3).
"""
import ast
import os
import struct
import sys
import urllib.request

URL = ("https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main/"
       "openwakeword_features_ACAV100M_2000_hrs_16bit.npy")
OUT = sys.argv[1] if len(sys.argv) > 1 else "acav_subset.npy"
TARGET_BYTES = int(float(sys.argv[2]) * 1024**3) if len(sys.argv) > 2 else 4 * 1024**3
CHUNKS = 8
PHASE = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0   # 0.5 = chunks halfway between the first run's


def fetch(start, end):
    req = urllib.request.Request(URL, headers={"Range": f"bytes={start}-{end}", "User-Agent": "oww-subset"})
    return urllib.request.urlopen(req, timeout=120)


head = fetch(0, 4095).read()
assert head[:6] == b"\x93NUMPY", "not an npy file"
major = head[6]
if major == 1:
    hlen = struct.unpack("<H", head[8:10])[0]
    offset = 10 + hlen
else:
    hlen = struct.unpack("<I", head[8:12])[0]
    offset = 12 + hlen
header = ast.literal_eval(head[offset - hlen:offset].decode("latin1"))
shape, descr = header["shape"], header["descr"]
assert not header["fortran_order"]
itemsize = int(descr[-1])
row_bytes = itemsize
for d in shape[1:]:
    row_bytes *= d
total_rows = shape[0]
take_rows = min(total_rows, TARGET_BYTES // row_bytes)
per_chunk = take_rows // CHUNKS
take_rows = per_chunk * CHUNKS
print(f"source shape={shape} dtype={descr} row={row_bytes} B; taking {take_rows} rows "
      f"({take_rows * row_bytes / 1024**3:.2f} GB) in {CHUNKS} chunks", flush=True)

new_header = repr({"descr": descr, "fortran_order": False, "shape": (take_rows,) + tuple(shape[1:])})
pad = 64 - (10 + len(new_header) + 1) % 64
new_header = new_header + " " * pad + "\n"
tmp = OUT + ".part"
with open(tmp, "wb") as f:
    f.write(b"\x93NUMPY\x01\x00" + struct.pack("<H", len(new_header)) + new_header.encode("latin1"))
    for i in range(CHUNKS):
        start_row = int((i + PHASE) * total_rows / CHUNKS)
        start = offset + start_row * row_bytes
        end = start + per_chunk * row_bytes - 1
        got = 0
        with fetch(start, end) as r:
            while True:
                buf = r.read(8 * 1024 * 1024)
                if not buf:
                    break
                f.write(buf)
                got += len(buf)
        assert got == per_chunk * row_bytes, f"chunk {i}: got {got}, want {per_chunk * row_bytes}"
        print(f"chunk {i + 1}/{CHUNKS} ok (rows {start_row}..{start_row + per_chunk})", flush=True)
os.replace(tmp, OUT)
print("DONE", OUT, os.path.getsize(OUT), "bytes", flush=True)
