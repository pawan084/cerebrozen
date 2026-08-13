"""Parsing an eligibility file, and refusing most of one.

An organisation's HR export is the single most likely way for wellbeing data to
arrive somewhere it must never be. The file that carries `email` and
`employee_id` is produced by the same system that holds absence reasons,
occupational-health flags and insurance categories, and the person exporting it
is usually not the person who read our documentation.

So the parser is an **allowlist over the header row**, not a denylist of
alarming words. `mood` and `diagnosis` are rejected, but so is
`wellbeing_score`, `eap_referral` and every column nobody has thought of yet —
a denylist only stops what its author imagined. Unknown means rejected, and the
whole file is rejected rather than the column dropped: a silently ignored column
teaches the administrator that sending it was fine.

Two more decisions worth keeping:

* **The group is not a CSV column.** It comes from the form, applied to every
  row. One less column is one less place to put something else.
* **Failures are reported by line number and the organisation's own
  `external_ref`, never by email address.** The seat list is deliberately not a
  roster of who holds a CereBro account, and an import report is part of the
  seat list.
"""
from __future__ import annotations

import csv
import io
from dataclasses import dataclass

#: Exactly the fields `MembershipCreate` accepts, minus `group_id`. Adding one
#: here means deciding it belongs in an employer's file — which is a different
#: question from whether the API accepts it.
ALLOWED_COLUMNS: frozenset[str] = frozenset({"email", "external_ref", "access_start", "access_end"})

#: Bounds, so a mis-selected file is an error rather than a memory event. Both
#: are generous next to a real contract: the largest seat count we sell is in
#: the hundreds.
MAX_BYTES = 1_000_000
MAX_ROWS = 5_000


class CsvRejected(ValueError):
    """The file as a whole is unusable. Nothing is imported."""


@dataclass(frozen=True)
class ParsedRow:
    #: 1-based line in the uploaded file, header included — so it matches what
    #: the administrator sees in their spreadsheet.
    line: int
    values: dict[str, str]


def _normalise(name: str) -> str:
    """Header names are matched forgivingly on FORM and strictly on MEANING.

    "Access End" and "access_end" are the same column; `access_ended_reason` is
    not, and is rejected.
    """
    return name.strip().lower().replace(" ", "_").lstrip("﻿")


def parse(text: str) -> list[ParsedRow]:
    """Rows from an eligibility CSV, or raise :class:`CsvRejected`.

    Only the file-level rules are enforced here. Per-row validity (a real email
    address, a date that is a date, a reference within bounds) is left to
    ``MembershipCreate`` in the route, so the CSV path and the single-invite
    path cannot drift apart on what a member row may contain.
    """
    if len(text.encode("utf-8")) > MAX_BYTES:
        raise CsvRejected(f"File is larger than {MAX_BYTES // 1000} KB — this is an eligibility list, not an export")

    reader = csv.reader(io.StringIO(text))
    try:
        header = next(reader)
    except StopIteration:
        raise CsvRejected("The file is empty") from None

    columns = [_normalise(c) for c in header]
    if not any(columns):
        raise CsvRejected("The first row must be a header naming the columns")

    unknown = [c for c in columns if c and c not in ALLOWED_COLUMNS]
    if unknown:
        # Named, not counted. An administrator who is told "unknown column" has
        # to guess which one, and guessing ends in a second upload of the same
        # personal data.
        raise CsvRejected(
            f"Unrecognised column(s): {', '.join(sorted(set(unknown)))}. "
            f"Eligibility files may contain only {', '.join(sorted(ALLOWED_COLUMNS))} — "
            "anything else, including anything about a person's health or wellbeing, "
            "is rejected rather than ignored."
        )
    if "email" not in columns:
        raise CsvRejected("The file needs an `email` column to identify each seat")

    rows: list[ParsedRow] = []
    for line, record in enumerate(reader, start=2):
        if not any(cell.strip() for cell in record):
            continue                                  # a blank line is not a row
        if len(rows) >= MAX_ROWS:
            raise CsvRejected(f"More than {MAX_ROWS} rows — split the file")
        values = {}
        for index, column in enumerate(columns):
            if not column:
                continue
            cell = record[index].strip() if index < len(record) else ""
            if cell:
                values[column] = cell
        if values:
            rows.append(ParsedRow(line=line, values=values))

    if not rows:
        raise CsvRejected("The file has a header but no rows")
    return rows
