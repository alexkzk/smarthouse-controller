import re
import os
import subprocess

class_map = {}
for root, _, files in os.walk("src/main/java"):
    for file in files:
        if file.endswith(".java"):
            class_name = file[:-5]
            rel_path = os.path.relpath(os.path.join(root, file), "src/main/java")
            fqn = rel_path.replace("/", ".")[:-5]
            class_map[class_name] = fqn

def add_import(file_path, fqn):
    with open(file_path, "r") as f:
        lines = f.read().split("\n")
    import_stmt = f"import {fqn};"
    if import_stmt in lines:
        return False
    for i, line in enumerate(lines):
        if line.startswith("package "):
            lines.insert(i + 1, import_stmt)
            break
    with open(file_path, "w") as f:
        f.write("\n".join(lines))
    return True

for iteration in range(10): # max 10 passes
    print(f"Pass {iteration + 1}...")
    result = subprocess.run(["./mvnw", "clean", "compile", "-B"], capture_output=True, text=True)
    if result.returncode == 0:
        print("Compilation SUCCESS!")
        break
        
    log = result.stdout
    errors = re.findall(r"\[ERROR\] (.*?):\[\d+,\d+\] cannot find symbol\n\[ERROR\]\s+symbol:\s+class (\w+)", log)
    
    fixed = 0
    for file_path, class_name in set(errors):
        if class_name in class_map:
            fqn = class_map[class_name]
            if add_import(file_path, fqn):
                fixed += 1
                
    if fixed == 0:
        print("Failed to fix any errors on this pass. Stopping.")
        print(log[-1000:])
        break
    print(f"Fixed {fixed} missing imports.")
