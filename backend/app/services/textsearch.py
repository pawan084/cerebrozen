"""LIKE/ILIKE escaping, shared by every search endpoint.

Promoted out of the journal routes (register C87/C88): the exact bug
`_escape_like` was written to fix — "100%" matching the whole table — sat
unfixed in `/content?q=` and `/admin/users?q=` one file over. One helper,
three call sites, no drift.
"""


def escape_like(term: str) -> str:
    """Neutralise LIKE's own wildcards so a search term is only ever text.

    Someone searching for "100%" means the characters, not "match everything",
    and "_" is a single-character wildcard nobody types on purpose. Use with
    ``.like(..., escape="\\\\")`` / ``.ilike(..., escape="\\\\")``.
    """
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
