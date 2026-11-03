#!/usr/bin/env python3
"""Prove the ten-language overlay upgrades the byte-pinned shared rc3 archive.

Uses production Java patch algorithms, the verified raw rc3 fixture, the current
signed archive, and the whole-module SHA of the retained d772 predecessor. No
APK, device or archive is created or modified.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
from runtime_stable_patches import load_chains, apply
from test_runtime_fixture import runtime

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
SHARED_ARCHIVE_SHA256 = 'd77261bf677a83c2440aab7a60cacf9c95876b59777b74f9de4fa6be9c2ba514'
PREDECESSOR_MODULE_SHA256 = '13a5c7426aa5c424ddaeba7fd1ab59f148f388b96241fc43fdd6cbe71a7d5255'

JAVA = r'''
import com.deepseekharness.app.util.ManagedPatchChain;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class LanguagePatchChainFixture {
  static void require(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
  static List<ManagedPatchChain.Patch> parts(Path root,String prefix)throws Exception{
    var result=new ArrayList<ManagedPatchChain.Patch>();
    for(int i=0;i<Integer.parseInt(Files.readString(root.resolve(prefix+"-count")));i++)
      result.add(new ManagedPatchChain.Patch(Files.readString(root.resolve(prefix+i+"-before")),Files.readString(root.resolve(prefix+i+"-after"))));
    return result;
  }
  public static void main(String[] args)throws Exception{
    Path root=Path.of(args[0]);
    var recipe=new ManagedPatchChain.Recipe("language-patch.json",parts(root,"new"),List.of(),parts(root,"old"));
    var chain=List.of(recipe);String expected=Files.readString(root.resolve("current.js"));
    String archive=Files.readString(root.resolve("archive.js"));
    for(String name:List.of("canonical.js","predecessor.js","archive.js","current.js")){
      String actual=ManagedPatchChain.apply(Files.readString(root.resolve(name)),chain,()->{throw new AssertionError("unexpected restore "+name);});
      require(actual.equals(expected),"direct differs "+name);
    }
    var reads=new AtomicInteger();
    String partial=ManagedPatchChain.apply(Files.readString(root.resolve("partial.js")),chain,()->{reads.incrementAndGet();return archive;});
    require(partial.equals(expected)&&reads.get()==1,"partial must restore pinned complete archive once");
    String predecessor=Files.readString(root.resolve("predecessor.js"));
    for(String source:List.of(Files.readString(root.resolve("partial.js")),predecessor+predecessor,"unknown managed source")){
      try{ManagedPatchChain.apply(source,chain,()->null);throw new AssertionError("missing canonical accepted");}
      catch(IOException error){require(error.getMessage().equals("PATCH_CANONICAL_MISSING"),"wrong missing canonical failure");}
      try{ManagedPatchChain.apply(source,chain,()->"invalid canonical");throw new AssertionError("invalid canonical accepted");}
      catch(IOException error){require(error.getMessage().equals("PATCH_CHAIN_MISMATCH"),"wrong chain failure");}
    }
    System.out.println("PASS real Java fresh npm/shared d772/current idempotence/partial canonical repair/duplicate and unknown fail-closed");
  }
}
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=ASSETS / 'dsh-runtime.bin')
    parser.add_argument('--jdk', type=Path)
    args = parser.parse_args()
    canonical_root = runtime('raw')
    recipe = json.loads((ASSETS / 'language-patch.json').read_text(encoding='utf8'))
    canonical = (canonical_root / 'node_modules' / recipe['module']).read_bytes()
    assert sha(canonical) == recipe['sourceSha256'], 'LOCKED_RC3_LOCALE_SOURCE'
    archive_sha = sha(args.archive.read_bytes())
    current_proof = json.loads((ASSETS / 'dsh-runtime.inputs.json').read_text(encoding='utf8'))
    assert archive_sha == current_proof['archive_sha256'], 'CURRENT_SIGNED_ARCHIVE_IDENTITY'
    target = 'usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/' + recipe['module']
    found = []
    with tarfile.open(args.archive, 'r|gz') as tree:
        for member in tree:
            if member.name.removeprefix('./') == target:
                assert member.isfile() and member.size < 8 * 1024 * 1024, 'ARCHIVE_LOCALE_MEMBER'
                found.append(tree.extractfile(member).read())
    assert len(found) == 1, 'ARCHIVE_LOCALE_UNIQUENESS'
    archived = found[0]
    previous = canonical.decode('utf8')
    for part in recipe['previousPatches']:
        assert previous.count(part['before']) == 1
        previous = previous.replace(part['before'], part['after'])
    predecessor = previous.encode('utf8')
    assert sha(predecessor) == PREDECESSOR_MODULE_SHA256, 'EXACT_D772_PREDECESSOR_MODULE_IDENTITY'
    chains, _ = load_chains(ASSETS, recipe['dshVersion'])
    chain = chains[recipe['module']]
    expected = apply(canonical, chain)
    assert archived == (predecessor if archive_sha == SHARED_ARCHIVE_SHA256 else expected), 'CURRENT_ARCHIVE_COMPLETE_LOCALE_FORM'
    assert apply(predecessor, chain) == expected
    assert apply(archived, chain) == expected
    assert apply(expected, chain) == expected
    partial = previous.replace(recipe['previousPatches'][1]['after'], recipe['previousPatches'][1]['before'])
    try:
        apply(partial.encode('utf8'), chain)
    except ValueError as error:
        assert 'STABLE_PATCH_ANCHOR' in str(error)
    else:
        raise AssertionError('partial predecessor accepted directly')
    output = Path(tempfile.mkdtemp(prefix='language-patch-chain-', dir=ROOT / 'app/build'))
    for name, data in [('canonical.js', canonical), ('predecessor.js', predecessor), ('archive.js', archived), ('current.js', expected), ('partial.js', partial.encode('utf8'))]:
        (output / name).write_bytes(data)
    current_parts = [(part['before'], (ASSETS / part['prependAsset']).read_text(encoding='utf8') + '\n' + part['after']
                      if 'prependAsset' in part else part['after']) for part in recipe['patches']]
    for prefix, parts in [('new', current_parts), ('old', [(part['before'], part['after']) for part in recipe['previousPatches']])]:
        (output / (prefix + '-count')).write_bytes(str(len(parts)).encode())
        for index, (before, after) in enumerate(parts):
            (output / (prefix + str(index) + '-before')).write_bytes(before.encode('utf8'))
            (output / (prefix + str(index) + '-after')).write_bytes(after.encode('utf8'))
    fixture = output / 'LanguagePatchChainFixture.java'
    fixture.write_bytes(JAVA.encode('utf8'))
    classes = output / 'classes'
    classes.mkdir()
    jdk = args.jdk or (Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None)
    suffix = '.exe' if os.name == 'nt' else ''
    javac = str(jdk / 'bin' / ('javac' + suffix)) if jdk else shutil.which('javac')
    java = str(jdk / 'bin' / ('java' + suffix)) if jdk else shutil.which('java')
    if not javac or not java:
        raise RuntimeError('JDK17_REQUIRED')
    source = ROOT / 'app/src/main/java/com/deepseekharness/app/util'
    ui_text = ROOT / 'tools/fixtures/documents-parent/com/deepseekharness/app/util/UiText.java'
    compiled = subprocess.run([javac, '-encoding', 'UTF-8', '--release', '17', '-d', str(classes),
                               str(source / 'ManagedPatchChain.java'), str(source / 'ExactTextPatch.java'), str(ui_text), str(fixture)],
                              capture_output=True, timeout=60)
    (output / 'compile.log').write_bytes(compiled.stdout + compiled.stderr)
    assert compiled.returncode == 0, str(output / 'compile.log')
    ran = subprocess.run([java, '-cp', str(classes), 'LanguagePatchChainFixture', str(output)], capture_output=True, timeout=30)
    (output / 'java.log').write_bytes(ran.stdout + ran.stderr)
    assert ran.returncode == 0, str(output / 'java.log')
    report = dict(status='PASS', archiveSha256=archive_sha, sourceSha256=sha(canonical),
                  predecessorArchiveSha256=SHARED_ARCHIVE_SHA256, predecessorModuleSha256=PREDECESSOR_MODULE_SHA256,
                  predecessorRecheckedFromActualArchive=archive_sha == SHARED_ARCHIVE_SHA256,
                  archivedModuleSha256=sha(archived), currentModuleSha256=sha(expected),
                  currentModuleBytes=len(expected), output=str(output), deviceValidation=False)
    (output / 'result.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
