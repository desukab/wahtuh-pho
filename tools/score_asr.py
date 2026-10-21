#!/usr/bin/env python3
"""Dependency-free, reproducible WER/CER scorer for Wahtuh Pho ASR runs.

Usage:
  python3 tools/score_asr.py --manifest docs/eval_manifest.json \
    --runs docs/bench_runs_koyal.jsonl

Each JSONL run must contain clip_id, text, and optionally model, lang, exit_code.
Only successful runs are scored. Reports micro-averaged error rates by model/language.
Normalization is NFC + lowercase + remove Unicode punctuation/symbols + whitespace.
This matches the normalization policy documented in docs/EVALUATION.md §6.2.
"""
from __future__ import annotations

import argparse
import json
import unicodedata
from collections import defaultdict
from pathlib import Path


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFC", str(text or "")).lower()
    kept = []
    for char in text:
        category = unicodedata.category(char)
        if category.startswith(("P", "S")):
            continue
        kept.append(char)
    return " ".join("".join(kept).split())


def edit_distance(left: list[str], right: list[str]) -> int:
    """Levenshtein distance using O(min(n,m)) memory."""
    if len(left) < len(right):
        left, right = right, left
    previous = list(range(len(right) + 1))
    for i, a in enumerate(left, 1):
        current = [i]
        for j, b in enumerate(right, 1):
            current.append(min(
                current[-1] + 1,
                previous[j] + 1,
                previous[j - 1] + (a != b),
            ))
        previous = current
    return previous[-1]


def errors(reference: str, hypothesis: str, unit: str) -> tuple[int, int]:
    ref = normalize(reference)
    hyp = normalize(hypothesis)
    if unit == "word":
        a, b = ref.split(), hyp.split()
    else:
        # Code-point CER after NFC normalization; Telugu combining sequences
        # are normalized before counting, avoiding canonically equivalent errors.
        a, b = list(ref.replace(" ", "")), list(hyp.replace(" ", ""))
    return edit_distance(a, b), len(a)


def load_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as stream:
        for line_no, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no}: invalid JSON: {exc}") from exc
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--runs", type=Path, required=True,
                        help="JSONL rows with clip_id, text, model, and optional exit_code")
    parser.add_argument("--json-out", type=Path,
                        help="Optional path to write machine-readable aggregate results")
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    references = {row["id"]: row for row in manifest}
    if len(references) != len(manifest):
        parser.error("manifest contains duplicate clip ids")

    aggregates = defaultdict(lambda: {
        "clips": 0, "failed": 0, "wer_errors": 0, "wer_words": 0,
        "cer_errors": 0, "cer_chars": 0,
    })
    seen = set()
    for run in load_jsonl(args.runs):
        clip_id = run.get("clip_id") or run.get("id")
        if clip_id not in references:
            raise ValueError(f"run references unknown clip_id: {clip_id!r}")
        model = str(run.get("model") or "unspecified")
        lang = str(run.get("lang") or references[clip_id].get("lang") or "unknown")
        key = (model, lang, clip_id)
        if key in seen:
            raise ValueError(f"duplicate run for model/language/clip: {key}")
        seen.add(key)
        bucket = aggregates[(model, lang)]
        if run.get("exit_code", 0) != 0 or run.get("error"):
            bucket["failed"] += 1
            continue
        reference = references[clip_id]["reference"]
        hypothesis = run.get("text", "")
        we, wn = errors(reference, hypothesis, "word")
        ce, cn = errors(reference, hypothesis, "char")
        bucket["clips"] += 1
        bucket["wer_errors"] += we
        bucket["wer_words"] += wn
        bucket["cer_errors"] += ce
        bucket["cer_chars"] += cn

    results = []
    print(f'{"MODEL":<24} {"LANG":<8} {"N":>4} {"FAILED":>7} {"WER":>9} {"CER":>9}')
    for (model, lang), b in sorted(aggregates.items()):
        wer = b["wer_errors"] / b["wer_words"] if b["wer_words"] else None
        cer = b["cer_errors"] / b["cer_chars"] if b["cer_chars"] else None
        row = {
            "model": model, "lang": lang, **b,
            "wer": wer, "cer": cer,
            "wer_percent": 100 * wer if wer is not None else None,
            "cer_percent": 100 * cer if cer is not None else None,
        }
        results.append(row)
        fmt = lambda value: "n/a" if value is None else f"{value * 100:.2f}%"
        print(f'{model[:24]:<24} {lang:<8} {b["clips"]:>4} {b["failed"]:>7} '
              f'{fmt(wer):>9} {fmt(cer):>9}')
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n",
                                 encoding="utf-8")
    if not results:
        print("No runs found.")
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
