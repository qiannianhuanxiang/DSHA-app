#!/usr/bin/env python3
"""为当前完整成功的 Sunset CI 生成不可覆盖的插件变更历史预发布。"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "ym2025szz/DSHA-prerelase"
BRANCH = "Sunset-3090-Q"
WORKFLOW = ".github/workflows/sunset-3090-q.yml"
BRANCH_REF = "refs/remotes/origin/" + BRANCH
SHA = re.compile(r"[a-f0-9]{40}")
POSITIVE = re.compile(r"[1-9][0-9]*")


def require(condition, code):
    if not condition:
        raise ValueError(code)


def identity(environment):
    """只接受真实工作流提供的完整提交和运行身份，不接受任意目标提交参数。"""
    require(environment.get("GITHUB_REPOSITORY") == REPOSITORY, "SUNSET_REPOSITORY")
    require(environment.get("SUNSET_REPOSITORY_PRIVATE") == "true", "SUNSET_PRIVATE_REPOSITORY")
    require(environment.get("GITHUB_EVENT_NAME") == "workflow_dispatch", "SUNSET_DISPATCH_ONLY")
    require(environment.get("GITHUB_REF") == "refs/heads/" + BRANCH, "SUNSET_BRANCH")
    require(environment.get("GITHUB_WORKFLOW_REF") == f"{REPOSITORY}/{WORKFLOW}@refs/heads/{BRANCH}",
            "SUNSET_WORKFLOW")
    commit = environment.get("GITHUB_SHA", "")
    require(SHA.fullmatch(commit), "SUNSET_FULL_COMMIT_REQUIRED")
    require(environment.get("GITHUB_WORKFLOW_SHA") == commit, "SUNSET_WORKFLOW_COMMIT")
    run_id = environment.get("GITHUB_RUN_ID", "")
    attempt = environment.get("GITHUB_RUN_ATTEMPT", "")
    require(POSITIVE.fullmatch(run_id) and POSITIVE.fullmatch(attempt), "SUNSET_RUN_IDENTITY")
    return dict(repository=REPOSITORY, branch=BRANCH, commit=commit, runId=run_id, runAttempt=attempt,
                tag=f"sunset-plugin-{commit[:12]}-r{run_id}-a{attempt}")


def git(root, *args, allowed=(0,)):
    # 不让本地 replace ref 改写已绑定提交的父链；所有输入都以 argv 传递。
    environment = dict(os.environ, GIT_NO_REPLACE_OBJECTS="1", GIT_TERMINAL_PROMPT="0")
    result = subprocess.run(["git", "--no-pager", *args], cwd=root, env=environment,
                            text=True, encoding="utf-8", capture_output=True, timeout=60)
    require(result.returncode in allowed, "SUNSET_GIT_FAILED: " + args[0])
    return result


def commit_object(root, ref):
    result = git(root, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}", allowed=(0, 128))
    require(result.returncode == 0 and SHA.fullmatch(result.stdout.strip()), "SUNSET_COMMIT_NOT_FOUND")
    return result.stdout.strip()


def prepare(root, environment, baseline, baseline_sha):
    """本地只读预检；tag/分支/短 SHA 不得偷偷解析为另一个发布目标。"""
    result = identity(environment)
    require(git(root, "rev-parse", "--is-shallow-repository").stdout.strip() == "false",
            "SUNSET_FULL_HISTORY_REQUIRED")
    require(commit_object(root, "HEAD") == result["commit"], "SUNSET_HEAD_COMMIT_MISMATCH")
    require(not git(root, "status", "--porcelain", "--untracked-files=no").stdout.strip(),
            "SUNSET_TRACKED_SOURCE_CHANGED")
    require(SHA.fullmatch(baseline_sha), "SUNSET_BASE_FULL_SHA_REQUIRED")
    require(isinstance(baseline, str) and baseline and baseline == baseline.strip(), "SUNSET_BASE_REQUIRED")
    if SHA.fullmatch(baseline):
        ref = baseline
    else:
        # 普通名称只查 tags，不接受 branch、HEAD、revision 表达式或短 SHA。
        require(not re.fullmatch(r"[a-fA-F0-9]{4,39}", baseline), "SUNSET_BASE_TAG_OR_FULL_SHA")
        ref = baseline if baseline.startswith("refs/tags/") else "refs/tags/" + baseline
        require(not baseline.startswith("refs/") or baseline.startswith("refs/tags/"),
                "SUNSET_BASE_TAG_OR_FULL_SHA")
        require(git(root, "check-ref-format", ref, allowed=(0, 1)).returncode == 0,
                "SUNSET_BASE_TAG_OR_FULL_SHA")
    base = commit_object(root, ref)
    require(base == baseline_sha, "SUNSET_BASE_SHA_MISMATCH")
    require(git(root, "merge-base", "--is-ancestor", base, result["commit"], allowed=(0, 1)).returncode == 0,
            "SUNSET_BASE_NOT_ANCESTOR")
    branch_tip = commit_object(root, BRANCH_REF)
    require(git(root, "merge-base", "--is-ancestor", result["commit"], branch_tip, allowed=(0, 1)).returncode == 0,
            "SUNSET_COMMIT_NOT_ON_BRANCH")
    tag_ref = "refs/tags/" + result["tag"]
    require(git(root, "show-ref", "--verify", "--quiet", tag_ref, allowed=(0, 1)).returncode == 1,
            "SUNSET_TAG_EXISTS")
    require(base != result["commit"], "SUNSET_EMPTY_HISTORY")
    result.update(schema=1, kind="sunset-plugin-history", workflow=WORKFLOW,
                  baseline=dict(input=baseline, commit=base), branchTip=branch_tip,
                  runUrl=f"https://github.com/{REPOSITORY}/actions/runs/{result['runId']}/attempts/{result['runAttempt']}")
    return result


def verify_jobs(plan, needs):
    """needs 来自同一 run；失败、跳过及旧 attempt 的回执全部拒绝。"""
    require(isinstance(needs, dict) and set(needs) == {"source", "apk"}, "SUNSET_REQUIRED_JOBS")
    for name in ("source", "apk"):
        job = needs[name]
        require(isinstance(job, dict) and job.get("result") == "success", "SUNSET_JOB_NOT_SUCCESS: " + name)
        outputs = job.get("outputs", {})
        require(isinstance(outputs, dict) and all(outputs.get(key) == plan[field] for key, field in (
            ("commit", "commit"), ("run_id", "runId"), ("run_attempt", "runAttempt"))),
            "SUNSET_JOB_IDENTITY: " + name)
    require(needs["source"]["outputs"].get("history_base_sha") == plan["baseline"]["commit"],
            "SUNSET_SOURCE_BASE_MISMATCH")
    apk = needs["apk"]["outputs"]
    artifacts = {}
    for name in ("apk", "evidence"):
        artifact_id = apk.get(name + "_artifact_id", "")
        require(isinstance(artifact_id, str) and POSITIVE.fullmatch(artifact_id), "SUNSET_ARTIFACT_ID")
        artifacts[name] = dict(id=artifact_id,
                              url=f"https://github.com/{REPOSITORY}/actions/runs/{plan['runId']}/artifacts/{artifact_id}")
    require(artifacts["apk"]["id"] != artifacts["evidence"]["id"], "SUNSET_ARTIFACT_ID")
    return artifacts


def history(root, plan, needs):
    result = dict(plan, artifacts=verify_jobs(plan, needs), order="reverse-topological", commits=[])
    commits = git(root, "rev-list", "--reverse", "--topo-order",
                  plan["baseline"]["commit"] + ".." + plan["commit"], "--").stdout.splitlines()
    require(commits and commits[-1] == plan["commit"] and len(set(commits)) == len(commits),
            "SUNSET_HISTORY_INCOMPLETE")
    for commit in commits:
        require(SHA.fullmatch(commit), "SUNSET_HISTORY_COMMIT")
        metadata = git(root, "show", "--no-patch", "--format=%P%x00%cI%x00%B", commit, "--").stdout
        fields = metadata.split("\0")
        require(len(fields) == 3, "SUNSET_HISTORY_METADATA")
        parents, committed_at, message = fields
        result["commits"].append(dict(commit=commit, parents=parents.split(), committedAt=committed_at,
                                      message=message.rstrip("\n")))
    return result


def render(report):
    header = [
        "# Sunset 插件管理变更历史", "",
        f"- 仓库：`{report['repository']}`；分支：`{report['branch']}`",
        f"- 当前已验证提交：`{report['commit']}`",
        f"- 历史基线（不含）：`{report['baseline']['commit']}`",
        f"- 唯一标签：`{report['tag']}`",
        f"- [本次完整 CI，attempt {report['runAttempt']}]({report['runUrl']})",
        f"- 提交数：{len(report['commits'])}；按父先子后的拓扑顺序，保留合并提交和全部提交正文。",
        "",
        "本次 source 与 apk jobs 均成功：Standard 源码模式/完整输入单测、宿主回归、Lint、SunsetRNE 签名与包内校验。",
        "SunsetRNE 是测试签名身份，不是 E7E3 正式包；本记录不代表设备安装或真机验收。",
        f"[本次测试 APK]({report['artifacts']['apk']['url']}) · [签名、输入与测试证据]({report['artifacts']['evidence']['url']})",
        "APK 与验证证据沿用 Actions 的 14 天保留期；本预发布附件只永久保留完整历史，不是完整源码或正式 APK 交付。",
        "",
    ]
    lines = list(header)
    for index, commit in enumerate(report["commits"], 1):
        message = commit["message"]
        # 提交正文只当数据写入代码块，不能逃逸为 Markdown/提及或工作流命令。
        fence = "`" * max(3, max((len(match.group()) + 1 for match in re.finditer(r"`+", message)), default=3))
        lines.extend([f"## {index}. {commit['commit']}", "",
                      f"提交时间：{commit['committedAt']}",
                      "父提交：" + ", ".join(commit["parents"]), "", fence, message, fence, ""])
    full = "\n".join(lines)
    # GitHub 正文有长度限制；超长时仍附完整 Markdown/JSON，绝不静默裁剪历史。
    body = full if len(full.encode("utf-8")) <= 100000 else "\n".join(header + [
        "完整历史超出 Release 正文预算，请阅读本 Release 的 history.md / history.json 附件。", ""])
    return full, body


def write_history(directory, report):
    # CREATE_NEW：已有目录/未知文件一律保留，失败现场也不清理或覆盖。
    directory = Path(directory)
    directory.mkdir(mode=0o700, exist_ok=False)
    full, body = render(report)
    files = {"history.md": full, "history.json": json.dumps(report, ensure_ascii=False, indent=2) + "\n",
             "release-body.md": body}
    for name, value in files.items():
        with (directory / name).open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(value)


def gh(arguments, payload=None):
    """仅显式 publish 调用；沿用 Actions GH_TOKEN，不读取任何本地凭据文件。"""
    try:
        result = subprocess.run(["gh", *arguments], input=payload, text=True, encoding="utf-8",
                                capture_output=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ValueError("SUNSET_REMOTE_OUTCOME_UNKNOWN: 请检查 tag/Release；不要自动重试") from error
    require(result.returncode == 0, "SUNSET_REMOTE_FAILED_OR_UNKNOWN: 请检查 tag/Release；不要自动重试")
    return result.stdout


def publish(report, directory, execute=gh):
    """只创建一次；POST ref 原子拒绝已有 tag，Release 不使用 edit/upload --clobber。"""
    tag = report["tag"]
    endpoint = f"repos/{REPOSITORY}/git/refs"
    created = json.loads(execute(["api", "--hostname", "github.com", "--method", "POST", endpoint, "--input", "-"],
                                json.dumps(dict(ref="refs/tags/" + tag, sha=report["commit"]))))
    require(created.get("ref") == "refs/tags/" + tag
            and created.get("object", {}).get("type") == "commit"
            and created.get("object", {}).get("sha") == report["commit"], "SUNSET_CREATED_TAG_MISMATCH")
    directory = Path(directory)
    execute(["release", "create", tag, "--repo", "github.com/" + REPOSITORY, "--verify-tag", "--target", report["commit"],
             "--prerelease", "--latest=false", "--title", "Sunset plugin history " + tag,
             "--notes-file", str(directory / "release-body.md"), str(directory / "history.md"),
             str(directory / "history.json")])
    # 写后只读核验；不把丢回执解释为未执行，不补写或移动旧 tag。
    actual = json.loads(execute(["api", "--hostname", "github.com",
                                f"repos/{REPOSITORY}/git/ref/tags/{tag}"]))
    release = json.loads(execute(["api", "--hostname", "github.com",
                                 f"repos/{REPOSITORY}/releases/tags/{tag}"]))
    require(actual.get("object", {}).get("type") == "commit"
            and actual.get("object", {}).get("sha") == report["commit"], "SUNSET_PUBLISHED_TAG_MISMATCH")
    require(release.get("tag_name") == tag and release.get("prerelease") is True
            and release.get("draft") is False, "SUNSET_PUBLISHED_RELEASE_MISMATCH")
    assets = release.get("assets", [])
    require(len(assets) == 2 and {asset.get("name") for asset in assets} == {"history.md", "history.json"}
            and all(asset.get("state") == "uploaded"
                    and asset.get("size") == (directory / asset["name"]).stat().st_size for asset in assets),
            "SUNSET_HISTORY_ASSETS_INCOMPLETE")
    return f"https://github.com/{REPOSITORY}/releases/tag/{tag}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("preflight", "render", "publish"))
    parser.add_argument("--base", required=True, help="已存在 tag 名或完整 40 位 commit SHA")
    parser.add_argument("--base-sha", required=True, help="基线解析后的完整 commit SHA；必须与 --base 一致")
    parser.add_argument("--output", type=Path, help="新建的历史输出目录；拒绝已有目录")
    parser.add_argument("--github-output", action="store_true")
    args = parser.parse_args()
    plan = prepare(ROOT, os.environ, args.base, args.base_sha)
    if args.command != "preflight":
        require(args.output is not None, "SUNSET_OUTPUT_REQUIRED")
        report = history(ROOT, plan, json.loads(os.environ.get("SUNSET_NEEDS", "null")))
        write_history(args.output, report)
        if args.command == "publish":
            print(publish(report, args.output))
    if args.github_output:
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"history_base_sha={plan['baseline']['commit']}\n")
    print(json.dumps({key: plan[key] for key in ("commit", "runId", "runAttempt", "tag")}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
