"""Lists methods and fields the test APK references that neither APK defines (by class, name, signature).

Run through check.sh. Usage: dexrefs.py <dexdump> <app dex dir> <test dex dir>
Only references into app-shared namespaces are checked; the framework (android/, java/) is skipped.
"""
import re, subprocess, sys, glob
dd, appdir, testdir = sys.argv[1:4]
SKIP = ("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/", "Lsun/", "Lorg/apache/http")

SUPER = {}

def defs(dexes):
    classes, members = set(), set()
    for d in dexes:
        out = subprocess.run([dd, d], capture_output=True, text=True).stdout
        cls = None; name = None
        for line in out.splitlines():
            m = re.search(r"Class descriptor\s+: '(L[^']+;)'", line)
            if m: cls = m.group(1); classes.add(cls); continue
            m = re.search(r"Superclass\s+: '(L[^']+;)'", line)
            if m and cls: SUPER[cls] = m.group(1); continue
            m = re.search(r"^\s+name\s+: '([^']+)'", line)
            if m: name = m.group(1); continue
            m = re.search(r"^\s+type\s+: '([^']+)'", line)
            if m and cls and name is not None:
                members.add((cls, name, m.group(1))); name = None
    return classes, members

def refs(dexes):
    out = set()
    pat = re.compile(r"(L[\w/$]+;)\.([\w$<>]+):(\S+)")
    for d in dexes:
        txt = subprocess.run([dd, "-d", d], capture_output=True, text=True).stdout
        for m in pat.finditer(txt):
            out.add((m.group(1), m.group(2), m.group(3)))
    return out

app_c, app_m = defs(glob.glob(appdir + "/*.dex"))
test_c, test_m = defs(glob.glob(testdir + "/*.dex"))
classes = app_c | test_c; members = app_m | test_m
def defined(c, n, sig):
    seen = set()
    while c and c not in seen:
        if (c, n, sig) in members: return True
        seen.add(c); c = SUPER.get(c)
    return False

missing = sorted(r for r in refs(glob.glob(testdir + "/*.dex"))
                 if not r[0].startswith(SKIP) and r[0] not in test_c and not defined(*r))
for c, n, s in missing:
    print(("CLASS-MISSING " if c not in classes else "") + f"{c}.{n}:{s}")
