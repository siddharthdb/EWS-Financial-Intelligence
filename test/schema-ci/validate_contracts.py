#!/usr/bin/env python3
"""Parse Avro contracts (shared types first) and check the topic registry, alias list and diagram.

The diagram renderer is intentionally duplicated in
test/ews-event-contracts-test/.../GovernanceContractTest.java. Keep the two in lockstep.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from fastavro.schema import parse_schema

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_avro() -> None:
    events = ROOT / "schemas" / "events"
    common = sorted((events / "common").glob("*.avsc"))
    rest = sorted(
        path for path in events.rglob("*.avsc") if "common" not in path.parts
    )
    if not common or not rest:
        fail(f"expected shared and payload Avro schemas, found {len(common)} common and {len(rest)} rest")
    named: dict = {}
    for path in common + rest:
        try:
            parse_schema(json.loads(path.read_text()), named)
        except Exception as exc:  # noqa: BLE001 - report every contract failure the same way
            fail(f"{path.relative_to(ROOT)}: {exc}")
        print(f"OK   {path.relative_to(ROOT)}")


def render(doc: dict) -> str:
    lines = [
        "%% Generated from schemas/topics/topic-registry-v1.json. Do not edit by hand.",
        "flowchart TB",
    ]
    topics = sorted(doc["topics"], key=lambda topic: topic["name"])
    events = doc["events"]
    for topic in topics:
        identifier = topic["name"].replace(".", "_").replace("-", "_")
        lines.append(f"  subgraph {identifier} [{topic['name']}]")
        owned = sorted(
            (event for event in events if event["topic"] == topic["name"]),
            key=lambda event: event["eventType"],
        )
        if not owned:
            lines.append(f'    {identifier}_none["(no catalogued events)"]')
        else:
            for index, event in enumerate(owned):
                lines.append(f'    {identifier}_{index}["{event["eventType"]}"]')
        lines.append("  end")
    lines.append("")
    return "\n".join(lines)


def check_registry_and_aliases() -> None:
    registry = json.loads((ROOT / "schemas/topics/topic-registry-v1.json").read_text())
    diagram = (ROOT / "docs/architecture/diagrams/topic-registry.mmd").read_text()
    if diagram != render(registry):
        fail("docs/architecture/diagrams/topic-registry.mmd is stale; regenerate it from the topic registry")

    aliases = json.loads((ROOT / "schemas/governance/deprecated-aliases-v1.json").read_text())
    deprecated = [item["deprecatedName"] for item in aliases["aliases"]]
    prefixes = [item["deprecatedPrefix"] for item in aliases["prefixRules"]]
    prohibited = aliases["prohibitedSignalTypes"]
    java_main = [
        path
        for base in (ROOT / "services", ROOT / "platform")
        for path in base.rglob("*.java")
        if "/src/main/" in path.as_posix()
    ]
    contracts = list(ROOT.joinpath("schemas").rglob("*.avsc"))
    contracts.extend(ROOT.joinpath("schemas").rglob("*.schema.json"))
    signal_contracts = list((ROOT / "schemas" / "signals").rglob("*.schema.json"))
    signal_contracts.extend((ROOT / "schemas" / "events" / "payloads").glob("signal-*.avsc"))

    def scan(paths: list[Path], names: list[str], prefix_rules: list[str], banned: list[str]) -> None:
        for path in paths:
            text = path.read_text()
            for name in names:
                if f'"{name}"' in text:
                    fail(f"{path.relative_to(ROOT)} contains deprecated name {name}")
            for prefix in prefix_rules:
                if re.search(rf'"{re.escape(prefix)}[A-Z0-9_]+"', text):
                    fail(f"{path.relative_to(ROOT)} contains deprecated prefix {prefix}")
            for name in banned:
                if f'"{name}"' in text:
                    fail(f"{path.relative_to(ROOT)} uses prohibited signal literal {name}")

    # Classification schemas are allowed to name ANALYTICAL_EWS and the jurisdiction namespaces.
    # Those names are prohibited only where a signal type would be declared.
    scan(contracts, deprecated, prefixes, [])
    scan(java_main, deprecated, prefixes, prohibited)
    scan(signal_contracts, [], [], prohibited)
    print("OK   topic registry, diagram and deprecated-alias scan")


def main() -> None:
    parse_avro()
    check_registry_and_aliases()


if __name__ == "__main__":
    main()
