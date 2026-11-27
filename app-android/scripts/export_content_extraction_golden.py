"""Golden files for the on-device content extraction port (contentextraction/ in the app).

Runs the harness in ba-auswertung (scripts/python/content_extraction) for the frozen configuration
D / rows / v1 and writes what ContentExtractionParityTest compares the Kotlin port against:

  <out>/screens/<snapshot_id>.json  input nodes, screen debug info, serialized screen, every call's
                                    body, prompt and template hash
  <out>/hints/<package>.txt         each app's rendered hint block. This exact text goes into the
                                    study's content extraction config.
  <out>/schema.json                 RESPONSE_SCHEMA
  <out>/manifest.json               configuration, snapshot ids, template hashes

Default input: the checked development snapshots from the staging test export (the harness's only
allowed data source). The output contains their screen text: write it OUTSIDE this repository.

  ~/Developer/ba-auswertung/.venv/bin/python -I app-android/scripts/export_content_extraction_golden.py \
      --harness ~/Developer/ba-auswertung/scripts/python --out ~/Developer/WHALE-Workspace/content-extraction-golden
  cd app-android && WHALE_CE_GOLDEN_DIR=~/Developer/WHALE-Workspace/content-extraction-golden \
      ./gradlew :app:testStagingDebugUnitTest --tests '*ContentExtractionParityTest*'

--synthetic <input.json> instead fills in the expected output for a hand-written screen
({"snapshot_id", "app_package", "nodes"}, no hint) and writes it to --out as one file; that is how
app/src/test/resources/contentextraction/synthetic_feed.json was made.
"""
import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

# Fields loader.to_tree reads; hand-written synthetic nodes may leave out the defaults.
NODE_DEFAULTS = {"className": None, "text": None, "contentDescription": None, "clickable": False, "scrollable": False}


def screen_record(snapshot_id, app_package, nodes, root, hint, p, build_screen, serialize, build_prompt):
    screen = build_screen(root, p)
    calls = []
    for i in range(len(screen.segments)):  # run.calls_for("D", ...)
        body = serialize(screen, p, [i])
        prompt = build_prompt("D", app_package, body, hint, "v1")
        calls.append({"segment": i, "body": body, "prompt": prompt,
                      "template_hash": hashlib.sha256(prompt.replace(body, "").encode()).hexdigest()[:12]})
    return {
        "snapshot_id": snapshot_id,
        "app_package": app_package,
        "nodes": nodes,
        "screen": {"mode": screen.mode, "main_id": screen.main_id, "main_fallback": screen.main_fallback,
                   "anchor_index": screen.offset, "anchor": screen.anchor,
                   "row_signatures": screen.row_signatures, "segments": len(screen.segments)},
        "serialized": serialize(screen, p),
        "calls": calls,
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--harness", required=True, type=Path, help="ba-auswertung/scripts/python")
    ap.add_argument("--out", required=True, type=Path, help="output directory (--synthetic: output file)")
    ap.add_argument("--synthetic", type=Path, help="hand-written screen to fill in instead of the dev set")
    a = ap.parse_args()

    sys.path.insert(0, str(a.harness.expanduser().resolve()))
    from content_extraction.loader import DEFAULT_SOURCE, load_snapshots, to_tree
    from content_extraction.model import Params
    from content_extraction.prompt import RESPONSE_SCHEMA, build_prompt
    from content_extraction.run import hints_for, label_ids
    from content_extraction.segment import build_screen
    from content_extraction.serialize import serialize

    p = Params(geometry="rows")  # D / rows / v1, everything else at its default
    fns = dict(build_screen=build_screen, serialize=serialize, build_prompt=build_prompt)

    if a.synthetic:
        doc = json.loads(a.synthetic.read_text())
        nodes = [NODE_DEFAULTS | n for n in doc["nodes"]]
        rec = screen_record(doc["snapshot_id"], doc["app_package"], nodes, to_tree(nodes), None, p, **fns)
        a.out.write_text(json.dumps(rec, ensure_ascii=False, indent=1) + "\n")
        print(f"{a.out}: {len(rec['calls'])} calls")
        return

    import polars as pl

    ids = label_ids(include_drafts=False)
    snaps = load_snapshots(snapshot_ids=ids)
    source = os.environ.get("BA_SOURCE_DIR") or DEFAULT_SOURCE  # as in loader.load_snapshots
    raw = dict(pl.scan_parquet(f"{source}/UI_Tree_Skeletons/**/*.parquet")
               .filter(pl.col("snapshot_id").is_in(ids))
               .select("snapshot_id", "skeleton_nodes").collect().iter_rows())
    test_ids = {s.snapshot_id for s in snaps}  # as in run.run_one
    hints = {app: hints_for(app, p, test_ids) for app in sorted({s.app_package for s in snaps})}

    (a.out / "screens").mkdir(parents=True, exist_ok=True)
    (a.out / "hints").mkdir(exist_ok=True)
    for app, text in hints.items():
        if text:
            (a.out / "hints" / f"{app}.txt").write_text(text)
    (a.out / "schema.json").write_text(json.dumps(RESPONSE_SCHEMA))

    template_hashes = set()
    for s in snaps:
        rec = screen_record(s.snapshot_id, s.app_package, raw[s.snapshot_id], s.root, hints.get(s.app_package), p, **fns)
        template_hashes |= {c["template_hash"] for c in rec["calls"]}
        (a.out / "screens" / f"{s.snapshot_id}.json").write_text(json.dumps(rec, ensure_ascii=False, indent=1))

    (a.out / "manifest.json").write_text(json.dumps({
        "configuration": "D / rows / v1", "params": {k: getattr(p, k) for k in p.__dataclass_fields__},
        "snapshot_ids": [s.snapshot_id for s in snaps], "template_hashes": sorted(template_hashes),
    }, indent=1))
    print(f"{len(snaps)} screens, template hashes {sorted(template_hashes)} -> {a.out}")


if __name__ == "__main__":
    main()
