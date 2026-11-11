"""Zip the pack deterministically and print sha1 (for server.properties resource-pack-sha1)."""
import hashlib, os, sys, zipfile
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resourcepack', 'hobbang-pack')
dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resourcepack', 'hobbang-pack.zip')
files = []
for root, _, fs in os.walk(src):
    for f in fs:
        p = os.path.join(root, f); files.append((os.path.relpath(p, src).replace(os.sep, '/'), p))
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for rel, p in sorted(files):
        zi = zipfile.ZipInfo(rel, date_time=(2026, 1, 1, 0, 0, 0)); zi.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(zi, open(p, 'rb').read())
h = hashlib.sha1(open(dst, 'rb').read()).hexdigest()
open(dst + '.sha1', 'w').write(h + '\n')
print(dst, os.path.getsize(dst), 'bytes  sha1', h)
