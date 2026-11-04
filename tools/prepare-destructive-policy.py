#!/usr/bin/env python3
"""Materialize the mandatory policy's classifier from its one canonical builtin source."""
from pathlib import Path
import argparse
ROOT=Path(__file__).resolve().parents[1]
def prepare(check=False):
    assets=ROOT/'app/src/main/assets'
    for name in ('destructive.js','workdir.js'):
        source=assets/'builtin-plugins/dsh-destructive-guard/lib'/name
        target=assets/'destructive-policy'/name
        data=source.read_bytes()
        if check:
            if not target.is_file() or target.read_bytes()!=data: raise SystemExit('DESTRUCTIVE_POLICY_COPY_CHANGED: '+name)
        else: target.write_bytes(data)
    print('PASS byte-exact mandatory policy classifier copies')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');prepare(parser.parse_args().check)
