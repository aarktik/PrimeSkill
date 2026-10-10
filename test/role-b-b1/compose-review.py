"""Compose B's metadata candidate onto the pinned, reviewed E source in a detached worktree."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

E_SHA = "742a0fa40f35499abe452d81b2756eec198cddac"
B_BASE = "04a9023d7b393b04eaf00835256b98cf11395b74"
B_PREPARATION = "4208e4562aaf728caefde4093664a17af0b6411b"


def git(directory, *args, input=None):
    return subprocess.run(["git", "-C", str(directory), *args], input=input,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path, help="Fresh detached worktree at reviewed E SHA")
    parser.add_argument("--resolved-web", action="store_true", help="Resume after manually resolving and staging web-only composition conflicts")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    candidate = args.candidate.resolve()
    if candidate == repo or git(candidate, "rev-parse", "HEAD").decode().strip() != E_SHA:
        raise SystemExit("Refusing to overwrite primary checkout or a different E snapshot")
    copies = [
        "code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java",
        "code/src/test/java/com/example/toolhub/service/ToolServiceImplTest.java",
        "code/src/test/java/com/example/toolhub/controller/api/ToolRestControllerTest.java",
    ]
    merged = [
        "code/src/main/java/com/example/toolhub/controller/web/ToolWebController.java",
        "code/src/main/resources/templates/tools/dashboard.html",
        "code/src/main/resources/templates/tools/form.html",
        "code/src/test/java/com/example/toolhub/controller/web/ToolWebControllerTest.java",
    ]
    # These E files exactly match B before preparation; copying adds B tests/guards,
    # rather than dropping E implementation. Fail if the assumption changes.
    for relative in copies:
        if git(repo, "show", f"{E_SHA}:{relative}") != git(repo, "show", f"{B_BASE}:{relative}"):
            raise SystemExit(f"Review differing E implementation before composing {relative}")
        current = (candidate / relative).read_bytes().replace(b"\r\n", b"\n")
        if current != git(repo, "show", f"{E_SHA}:{relative}"):
            raise SystemExit(f"Candidate already contains changes: {relative}")
    if args.resolved_web and git(candidate, "diff", "--name-only", "--diff-filter=U").strip():
        raise SystemExit("Resolve and stage composition conflicts before resuming")
    if not args.resolved_web and git(candidate, "diff", "--name-only", "HEAD", "--", *merged).strip():
        raise SystemExit("Candidate web files must be clean before 3-way composition")
    patch = git(repo, "diff", "--binary", B_PREPARATION, "--", *merged)
    # Preserve C browse routes, D review integration and E layout additions.
    if not args.resolved_web:
        try:
            git(candidate, "apply", "--3way", input=patch)
        except subprocess.CalledProcessError as failure:
            print(failure.stderr.decode("utf-8", errors="replace"))
            raise SystemExit("Resolve web conflicts preserving E/D content, stage the resolved files, then rerun with --resolved-web")
    manifest = {"reviewed_e_sha": E_SHA, "b_head": git(repo, "rev-parse", "HEAD").decode().strip(), "files": {}}
    for relative in copies:
        shutil.copyfile(repo / relative, candidate / relative)
    extra = ["code/src/main/resources/templates/tools/edit-error.html"]
    for relative in extra:
        shutil.copyfile(repo / relative, candidate / relative)
    for name in ("ToolMetadataContractPostgresIT.java", "ToolMetadataConcurrencyPostgresIT.java"):
        relative = "code/src/test/java/com/example/toolhub/" + name
        shutil.copyfile(repo / "test/role-b-b1" / name, candidate / relative)
        extra.append(relative)
    for relative in copies + merged + extra:
        manifest["files"][relative] = hashlib.sha256((candidate / relative).read_bytes()).hexdigest()
    (candidate / "b1-source-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Composed B metadata candidate with reviewed E source; no branch merge or push performed")
    print(candidate / "b1-source-manifest.json")


if __name__ == "__main__":
    main()
