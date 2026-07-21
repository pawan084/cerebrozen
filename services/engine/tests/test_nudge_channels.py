"""Channel-native nudge formatting — Slack / Teams / generic.

The delivery layer turns a content-free signal into a message the target chat surface
renders. Two things must hold: the shape is right for the surface, and rule 5 survives the
formatting — the visible text is a count and a link, never a commitment body or a session id.
"""

from __future__ import annotations

import pytest

from app import notifications

REC = {"user_id": "u", "org_id": "acme", "due_count": 2,
       "session_ids": ["s-secret-1", "s-secret-2"], "at": "2026-07-17T00:00:00"}


def test_channel_is_inferred_from_a_slack_webhook(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://hooks.slack.com/services/T/B/x")
    monkeypatch.delenv("CEREBROZEN_NUDGE_CHANNEL", raising=False)
    assert notifications.channel() == "slack"


def test_channel_is_inferred_from_a_teams_webhook(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://acme.webhook.office.com/webhookb2/x")
    monkeypatch.delenv("CEREBROZEN_NUDGE_CHANNEL", raising=False)
    assert notifications.channel() == "teams"


def test_explicit_channel_overrides_inference(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://hooks.slack.com/services/T/B/x")
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", "generic")
    assert notifications.channel() == "generic"


def test_generic_payload_is_the_raw_record_unchanged(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://hook.example/nudge")
    monkeypatch.delenv("CEREBROZEN_NUDGE_CHANNEL", raising=False)
    assert notifications._format_payload(REC) == REC  # backward compatible


def test_slack_payload_is_block_kit_and_carries_the_count(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", "slack")
    monkeypatch.setenv("CEREBROZEN_APP_DEEP_LINK", "https://app.example/checkins")
    payload = notifications._format_payload(REC)
    assert "blocks" in payload and payload["blocks"][0]["type"] == "section"
    assert "2 coaching check-ins due" in payload["text"]
    # a deep link becomes a button
    assert any(b.get("type") == "actions" for b in payload["blocks"])


def test_teams_payload_is_a_messagecard(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", "teams")
    monkeypatch.delenv("CEREBROZEN_APP_DEEP_LINK", raising=False)
    payload = notifications._format_payload(REC)
    assert payload["@type"] == "MessageCard"
    assert "2 coaching check-ins due" in payload["text"]


@pytest.mark.parametrize("ch", ["slack", "teams"])
def test_no_session_id_or_body_leaks_into_the_visible_message(monkeypatch, ch):
    """Rule 5 at the boundary: the rendered card must not contain a session id or any body."""
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", ch)
    payload = notifications._format_payload(REC)
    blob = str(payload)
    assert "s-secret-1" not in blob and "s-secret-2" not in blob


def test_singular_grammar(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", "slack")
    one = {**REC, "due_count": 1}
    assert "1 coaching check-in due" in notifications._format_payload(one)["text"]


# ── #68: a nudge may remind, never manipulate ────────────────────────────────

#: Vocabulary that turns a reminder into a retention lever. Not a style preference: an AI
#: that performs longing to pull a user back is the behaviour CA SB243 and NY's
#: companion-AI law are written about, and a notification is where it would appear first
#: because notification copy is the thing product teams A/B test hardest.
_MANIPULATIVE = (
    "miss you", "we miss", "missing you", "lonely", "come back to me", "don't leave",
    "abandoned", "still there", "waiting for you", "i need you", "haven't heard from you",
    "disappointed", "you promised", "don't let me down", "streak", "you'll lose",
)


@pytest.mark.parametrize("ch", ["slack", "teams", "generic"])
def test_a_nudge_never_uses_longing_guilt_or_loss_to_pull_the_user_back(monkeypatch, ch):
    """The engine's nudge is a count and a link by construction — this is the guard that
    keeps it that way when someone later "improves engagement" on this copy."""
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", ch)
    blob = str(notifications._format_payload(REC)).lower()
    for phrase in _MANIPULATIVE:
        assert phrase not in blob, f"the nudge copy leans on {phrase!r}"


def test_the_nudge_points_at_the_user_s_own_follow_through(monkeypatch):
    """The honest reason to reopen the app is the commitment the user made, not the coach's
    feelings about their absence."""
    monkeypatch.setenv("CEREBROZEN_NUDGE_CHANNEL", "slack")
    text = notifications._format_payload(REC)["text"].lower()
    assert "check-in" in text and "follow through" in text
