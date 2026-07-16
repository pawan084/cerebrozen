"""Prompt history and rollback — so a bad save is recoverable.

The workbook is an .xlsx edited in place. A save overwrote the sheet, `reg.version` ticked a
counter, and the audit line named the actor and the version numbers but NOT the text. The
console could destroy a 39,000-character coaching prompt with one PUT and offer no way back:
the only copy was in git, which an operator on a running deployment does not have.

The property everything else rests on is the ORDERING — snapshot BEFORE write. Snapshot
after, and the one edit you most need to undo (the one that just destroyed the text) is the
one that never got recorded.
"""

from __future__ import annotations

import pytest

from app.stores import prompt_versions


@pytest.fixture
def store(mongo):
    """A real document store; the module reads through app.stores.mongo.get_client."""
    return mongo


# ── the store ────────────────────────────────────────────────────────────────


def test_a_snapshot_comes_back(store):
    vid = prompt_versions.snapshot("core_coaching_agent", "the original text", actor="u1")
    assert vid
    got = prompt_versions.get("core_coaching_agent", vid)
    assert got["text"] == "the original text"
    assert got["actor"] == "u1"


def test_history_is_newest_first(store):
    for text in ("v1", "v2", "v3"):
        prompt_versions.snapshot("core_coaching_agent", text)
    sizes = [r["size"] for r in prompt_versions.history("core_coaching_agent")]
    assert len(sizes) == 3
    ats = [r["at"] for r in prompt_versions.history("core_coaching_agent")]
    assert ats == sorted(ats, reverse=True)


def test_history_carries_no_bodies(store):
    """60 versions of a 39k prompt is 2.3MB to render a table of dates."""
    prompt_versions.snapshot("core_coaching_agent", "x" * 5000)
    rows = prompt_versions.history("core_coaching_agent")
    assert rows and all("text" not in r for r in rows)
    assert rows[0]["size"] == 5000, "the size must survive even though the body does not"


def test_a_version_belongs_to_its_stage(store):
    """A mistyped id must not hand back another agent's prompt."""
    vid = prompt_versions.snapshot("core_coaching_agent", "core text")
    assert prompt_versions.get("feedback_mood_capture_agent", vid) is None


def test_history_is_bounded(store, monkeypatch):
    # An author iterating for an afternoon must not be able to fill the disk.
    monkeypatch.setattr(prompt_versions, "KEEP", 3)
    for i in range(6):
        prompt_versions.snapshot("core_coaching_agent", f"version {i}")
    assert len(prompt_versions.history("core_coaching_agent", limit=99)) == 3


def test_trimming_keeps_the_NEWEST(store, monkeypatch):
    monkeypatch.setattr(prompt_versions, "KEEP", 2)
    for i in range(4):
        prompt_versions.snapshot("core_coaching_agent", f"version {i}")
    kept = prompt_versions.history("core_coaching_agent", limit=99)
    bodies = [prompt_versions.get("core_coaching_agent", r["version_id"])["text"] for r in kept]
    assert "version 3" in bodies, "the most recent version was trimmed away"


def test_a_dead_store_never_blocks_an_edit():
    """No `store` fixture: get_client() returns None. An operator must not be unable to fix
    a bad prompt because the undo log is unavailable — that inverts the point of it."""
    assert prompt_versions.snapshot("core_coaching_agent", "text") is None
    assert prompt_versions.history("core_coaching_agent") == []
    assert prompt_versions.get("core_coaching_agent", "any") is None


def test_a_store_that_throws_is_not_fatal(monkeypatch):
    """Patches the REAL seam — the client — not `_collection` itself. Patching the wrapper
    would prove nothing about the wrapper, which is where the guard lives."""
    monkeypatch.setattr(
        "app.stores.mongo.get_client",
        lambda: (_ for _ in ()).throw(RuntimeError("the store is on fire")),
    )
    assert prompt_versions.snapshot("s", "t") is None
    assert prompt_versions.history("s") == []
    assert prompt_versions.get("s", "v") is None


def test_a_collection_that_throws_mid_write_is_not_fatal(store, monkeypatch):
    class Boom:
        def insert_one(self, *_a, **_k):
            raise RuntimeError("write failed")

        def find(self, *_a, **_k):
            raise RuntimeError("read failed")

        def find_one(self, *_a, **_k):
            raise RuntimeError("read failed")

    monkeypatch.setattr(prompt_versions, "_collection", lambda: Boom())
    assert prompt_versions.snapshot("s", "t") is None
    assert prompt_versions.history("s") == []
    assert prompt_versions.get("s", "v") is None


def test_the_hash_is_stable_and_content_addressed():
    a = prompt_versions.content_hash("same text")
    assert a == prompt_versions.content_hash("same text")
    assert a != prompt_versions.content_hash("different text")


# ── the ordering: the property everything rests on ───────────────────────────


def test_the_edit_route_snapshots_BEFORE_it_writes():
    """Pinned by source. Snapshot-after would record the text the author just typed and
    lose the one they wanted back — and no behavioural test on a passing edit would show
    the difference."""
    import inspect

    from app.routers import prompts

    src = inspect.getsource(prompts.edit_prompt)
    snap = src.index("prompt_versions.snapshot")
    write = src.index("_write_prompt_edit")
    assert snap < write, "the snapshot must precede the write, or the undo log records the wrong text"


def test_a_revert_is_itself_undoable():
    import inspect

    from app.routers import prompts

    src = inspect.getsource(prompts.revert_prompt)
    assert "prompt_versions.snapshot" in src, "an accidental revert must be recoverable too"
    assert src.index("prompt_versions.snapshot") < src.index("_write_prompt_edit")


def test_a_revert_still_validates():
    """A version can rot: the validator gains rules, the workbook's contract moves. There is
    no privileged path that skips the checks because the text used to live here."""
    import inspect

    from app.routers import prompts

    src = inspect.getsource(prompts.revert_prompt)
    assert "validate_prompt_text" in src
    assert src.index("validate_prompt_text") < src.index("_write_prompt_edit")
