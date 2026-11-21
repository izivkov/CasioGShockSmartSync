#!/usr/bin/env python3
"""List classes the APK's code refers to that no dex file in the APK defines (and the platform does not
provide). Such a reference is a NoClassDefFoundError on the device that compiles and unit-tests fine.
Usage: dex_missing_refs.py <apk> <dexdump>"""
import re, subprocess, sys, zipfile, tempfile, os, collections

apk, dexdump = sys.argv[1], sys.argv[2]
tmp = tempfile.mkdtemp()
with zipfile.ZipFile(apk) as z:
    dexes = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
    z.extractall(tmp, dexes)

defined = set()
referrers = collections.defaultdict(set)
cls_re = re.compile(r"Class descriptor\s+:\s+'(L[^']+;)'")
ref_re = re.compile(r"(?<![\w$/])(L[A-Za-z0-9_$/]+;)")
for d in dexes:
    out = subprocess.run([dexdump, "-d", os.path.join(tmp, d)], capture_output=True, text=True, errors="replace").stdout
    current = None
    for line in out.splitlines():
        m = cls_re.search(line)
        if m:
            current = m.group(1)
            defined.add(current)
            continue
        if current and re.search(r"\|[0-9a-f]{4}: (invoke|new-instance|const-class|check-cast|instance-of|sget|sput|iget|iput|filled-new-array)", line):
            for r in ref_re.findall(line):
                referrers[r].add(current)

platform = ("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/", "Llibcore/", "Lorg/json/", "Lorg/w3c/", "Lorg/xml/",
            "Lorg/apache/http/", "Lsun/", "Lcom/android/", "Ljunit/", "Lorg/junit/")
missing = {r: c for r, c in referrers.items() if r not in defined and not r.startswith(platform)}
print(f"dex files: {len(dexes)}, defined classes: {len(defined)}, referenced classes: {len(referrers)}")
print(f"MISSING referenced classes: {len(missing)}")
optional = ("Landroidx/", "Lcom/google/android/", "Lcom/google/common/", "Lcom/google/errorprone/", "Lcom/google/j2objc/")
for r in sorted(missing):
    # Optional vendor/tooling code inside androidx and guava is never called; show the rest, and anything of ours.
    if all(c.startswith(optional) for c in missing[r]) and not r.startswith(("Lorg/avmedia", "Lokhttp3", "Lokio", "Lcom/google/gson")):
        continue
    print(" ", r, "<-", ", ".join(sorted(missing[r])[:3]))
