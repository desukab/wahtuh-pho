"""Deepgram head-to-head benchmark (wahtuh-pho).

Runs the SAME 31 FLEURS clips through Deepgram nova-3 that the local
whisper benchmark used (docs/eval_manifest.json):
  - 15 Telugu clips with language=te
  - 15 English FLEURS clips + jfk.wav with language=en
  - all 30 FLEURS clips with language=multi (code-switching behavior)

Scoring matches docs/EVALUATION.md 6.2 exactly: jiwer 4.0.0 wer/cer on
NFC + lowercase + strip P*/S* + collapse-whitespace normalized text.

COST GUARDRAIL: estimates total cost before the first call and aborts if
it exceeds $0.50. Stops on the first non-200 response (no blind retries).
Requires a WORKING custom.deepgram credential (reconnect first if the
2026-10-19 401 persists).

Output: docs/bench_runs_deepgram.jsonl (one JSON object per call) +
printed aggregate table.
"""
import json
import subprocess
import sys
import unicodedata
import wave

import jiwer

MANIFEST = "/home/hatch/workspace/wahtuh-pho/docs/eval_manifest.json"
AUDIO_DIR = "/home/hatch/workspace/corpus/audio"
OUT = "/home/hatch/workspace/wahtuh-pho/docs/bench_runs_deepgram.jsonl"
HELPER = "/home/hatch/workspace/custom-connectors/custom.deepgram/bin/deepgram_listen.py"

COST_PER_MIN = {"mono": 0.0043, "multi": 0.0052}
COST_ABORT_USD = 0.50


def normalize(text):
    text = unicodedata.normalize("NFC", text).lower()
    out = []
    for ch in text:
        cat = unicodedata.category(ch)
        if cat.startswith("P") or cat.startswith("S"):
            continue
        out.append(ch)
    return " ".join("".join(out).split())


def audio_seconds(path):
    with wave.open(path, "rb") as w:
        return w.getnframes() / float(w.getframerate())


def main():
    manifest = json.load(open(MANIFEST))
    by_id = {m["id"]: m for m in manifest}

    # Plan: (clip_id, language_param)
    plan = []
    for m in manifest:
        if m["lang"] == "te":
            plan.append((m["id"], "te"))
    for m in manifest:
        if m["lang"] == "en":
            plan.append((m["id"], "en"))
    for m in manifest:
        if m["id"] != "jfk":
            plan.append((m["id"], "multi"))

    total_sec = sum(audio_seconds(f"{AUDIO_DIR}/{by_id[c]['file']}") for c, _ in plan)
    est_cost = sum(
        audio_seconds(f"{AUDIO_DIR}/{by_id[c]['file']}") / 60.0
        * (COST_PER_MIN["multi"] if lang == "multi" else COST_PER_MIN["mono"])
        for c, lang in plan
    )
    print(f"calls: {len(plan)}, audio: {total_sec:.0f}s, estimated cost: ${est_cost:.4f}")
    if est_cost > COST_ABORT_USD:
        print(f"ABORT: estimated cost ${est_cost:.4f} exceeds ${COST_ABORT_USD} guardrail")
        sys.exit(2)

    results = []
    with open(OUT, "w") as f:
        for clip_id, lang in plan:
            m = by_id[clip_id]
            wav = f"{AUDIO_DIR}/{m['file']}"
            print(f"... {clip_id} [{lang}]", flush=True)
            p = subprocess.run(
                [sys.executable, HELPER, wav, "--language", lang],
                capture_output=True, text=True, timeout=180,
            )
            try:
                rec = json.loads(p.stdout.strip().splitlines()[-1])
            except (IndexError, json.JSONDecodeError):
                print(f"STOP: helper failed on {clip_id}: {p.stderr[:300]}")
                sys.exit(1)
            if rec.get("http_status") != 200 or "transcript" not in rec:
                print(f"STOP: bad response on {clip_id}: {json.dumps(rec)[:300]}")
                sys.exit(1)
            rec["clip_id"] = clip_id
            rec["reference"] = m["reference"]
            rec["ref_lang"] = m["lang"]
            cost = rec["audio_sec"] / 60.0 * (
                COST_PER_MIN["multi"] if lang == "multi" else COST_PER_MIN["mono"]
            )
            rec["cost_usd"] = round(cost, 6)
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            f.flush()
            results.append(rec)

    # Aggregate by (language_param, ref_lang).
    cells = {}
    for r in results:
        key = (r["language"], r["ref_lang"])
        cells.setdefault(key, []).append(r)
    print("\n=== Deepgram nova-3 vs manifest references ===")
    total_cost = 0.0
    for (lang, ref_lang), rs in sorted(cells.items()):
        wers, cers, lats = [], [], []
        for r in rs:
            wers.append(jiwer.wer(normalize(r["reference"]), normalize(r["transcript"])))
            cers.append(jiwer.cer(normalize(r["reference"]), normalize(r["transcript"])))
            lats.append(r["latency_ms"])
            total_cost += r["cost_usd"]
        lats_sorted = sorted(lats)
        p50 = lats_sorted[int((len(lats_sorted) - 1) * 0.5)]
        p95 = lats_sorted[int((len(lats_sorted) - 1) * 0.95)]
        print(
            f"lang={lang:5s} ref={ref_lang} n={len(rs):2d} "
            f"mean WER={sum(wers)/len(wers):.3f} mean CER={sum(cers)/len(cers):.3f} "
            f"p50={p50}ms p95={p95}ms"
        )
    print(f"\nTOTAL SPEND: ${total_cost:.4f} over {len(results)} calls")
    print(f"jiwer {__import__('importlib.metadata', fromlist=['version']).version('jiwer')}")


if __name__ == "__main__":
    main()
