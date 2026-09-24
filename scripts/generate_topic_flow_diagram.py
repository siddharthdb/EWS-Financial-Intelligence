#!/usr/bin/env python3
"""Generates docs/architecture/09-generated-topic-flow-diagram.md from
docs/architecture/topic-registry.json.

Satisfies docs/architecture/05-coherence-review-parts-i-iii.md Section 5, backlog item 7
("Add architecture diagrams generated from the normalized Parts I-III model"). The diagram is
generated, not hand-drawn: every node/edge in the Mermaid graph and every row of the companion
table is derived directly from topic-registry.json, so it can never drift silently out of sync with
that registry (see --check below).

Usage:
    python3 scripts/generate_topic_flow_diagram.py            # regenerate the doc in place
    python3 scripts/generate_topic_flow_diagram.py --check    # fail if the committed doc is stale
"""

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
REGISTRY_PATH = REPO_ROOT / "docs" / "architecture" / "topic-registry.json"
OUTPUT_PATH = REPO_ROOT / "docs" / "architecture" / "09-generated-topic-flow-diagram.md"

_NON_WORD_RE = re.compile(r"[^A-Za-z0-9_]+")


def mermaid_id(name: str) -> str:
    """Mermaid node IDs must be a single token with no spaces/punctuation; the readable name still
    appears in the node's ["label"] text, so sanitizing the ID loses no information."""
    sanitized = _NON_WORD_RE.sub("_", name).strip("_")
    return "n_" + sanitized


def build_document(registry: dict) -> str:
    topics = registry["topics"]
    events = registry["events"]

    topic_names = [t["topic"] for t in topics]
    topic_retention = {t["topic"]: t["retentionMode"] for t in topics}

    owner_to_topic_events = defaultdict(lambda: defaultdict(list))
    for event in events:
        owner_to_topic_events[event["owner"]][event["topic"]].append(event["eventType"])

    lines = []
    lines.append("# EWS 2.0 — Generated Topic Flow Diagram")
    lines.append("")
    lines.append("**Status:** Generated — do not hand-edit")
    lines.append("**Source of truth:** `docs/architecture/topic-registry.json`")
    lines.append(
        "**Regenerate with:** `python3 scripts/generate_topic_flow_diagram.py` "
        "(checked for staleness by `python3 scripts/generate_topic_flow_diagram.py --check`, "
        "wired into CI)"
    )
    lines.append("")
    lines.append(
        "Satisfies `docs/architecture/05-coherence-review-parts-i-iii.md` Section 5, backlog item "
        "7 (\"Add architecture diagrams generated from the normalized Parts I-III model\"). Every "
        "node and edge below is derived directly from `topic-registry.json` — this file is "
        "regenerated whenever that registry changes, never hand-edited."
    )
    lines.append("")
    lines.append("## Owner service → topic flow")
    lines.append("")
    lines.append("```mermaid")
    lines.append("graph LR")

    owners = sorted(owner_to_topic_events.keys())
    for owner in owners:
        lines.append(f'  {mermaid_id(owner)}["{owner}"]')
    for topic in topic_names:
        lines.append(f'  {mermaid_id(topic)}(["{topic}"])')

    for owner in owners:
        for topic in sorted(owner_to_topic_events[owner].keys()):
            event_count = len(owner_to_topic_events[owner][topic])
            label = f"{event_count} event type" + ("s" if event_count != 1 else "")
            lines.append(f"  {mermaid_id(owner)} -->|{label}| {mermaid_id(topic)}")

    lines.append("```")
    lines.append("")
    lines.append("## Full event → topic → owner mapping")
    lines.append("")
    lines.append("| Event type | Topic | Key field | Owner | Data classification |")
    lines.append("|---|---|---|---|---|")
    for event in sorted(events, key=lambda e: (e["topic"], e["eventType"])):
        lines.append(
            f"| `{event['eventType']}` | `{event['topic']}` | `{event['keyField']}` | "
            f"{event['owner']} | {event['dataClassification']} |"
        )
    lines.append("")
    lines.append("## Declared topics and retention modes")
    lines.append("")
    lines.append("| Topic | Retention mode | Notes |")
    lines.append("|---|---|---|")
    for topic in topics:
        lines.append(f"| `{topic['topic']}` | {topic['retentionMode']} | {topic.get('notes', '')} |")
    lines.append("")

    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="Exit non-zero if the committed doc doesn't match what would be generated now.",
    )
    args = parser.parse_args()

    with open(REGISTRY_PATH) as f:
        registry = json.load(f)

    generated = build_document(registry)

    if args.check:
        if not OUTPUT_PATH.exists():
            print(f"FAIL: {OUTPUT_PATH} does not exist; run without --check to generate it.", file=sys.stderr)
            return 1
        current = OUTPUT_PATH.read_text()
        if current != generated:
            print(
                f"FAIL: {OUTPUT_PATH} is stale relative to {REGISTRY_PATH}. "
                "Run `python3 scripts/generate_topic_flow_diagram.py` and commit the result.",
                file=sys.stderr,
            )
            return 1
        print(f"OK: {OUTPUT_PATH} is up to date.")
        return 0

    OUTPUT_PATH.write_text(generated)
    print(f"Wrote {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
