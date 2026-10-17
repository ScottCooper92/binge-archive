"""Turns dexrefs.py output into ProGuard keep rules, one per class, listing the exact members."""
import re, sys, collections
PRIM = {"V": "void", "Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float", "D": "double"}

def jtype(desc, i=0):
    arr = 0
    while desc[i] == "[": arr += 1; i += 1
    if desc[i] == "L":
        j = desc.index(";", i); t = desc[i + 1:j].replace("/", "."); i = j + 1
    else:
        t = PRIM[desc[i]]; i += 1
    return t + "[]" * arr, i

def member(name, sig):
    if not sig.startswith("("):
        return f"{jtype(sig)[0]} {name};"
    params, i = [], 1
    while sig[i] != ")":
        t, i = jtype(sig, i); params.append(t)
    ret, _ = jtype(sig, i + 1)
    return f"{ret} {name}({','.join(params)});"

rules = collections.OrderedDict()
for line in sys.stdin:
    line = line.strip()
    line = line.removeprefix("CLASS-MISSING ")
    if not line or "androidx/concurrent/futures/SuspendToFutureAdapter" in line: continue
    m = re.match(r"(L[\w/$]+;)\.([\w$<>]+):(\S+)", line)
    cls = m.group(1)[1:-1].replace("/", ".")
    rules.setdefault(cls, []).append(member(m.group(2), m.group(3)))
for cls, members in rules.items():
    print(f"-keep class {cls} {{")
    for mem in sorted(set(members)): print(f"    {mem}")
    print("}")
