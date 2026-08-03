"""The user's chosen language reaches every generated reply.

Onboarding has always asked. Until 2026-07-30 only starter-topic generation
read the answer, so someone who picked Hindi got English back from the chat
reply, the daily plan and the Oracle — the three things they actually use.
"""
import uuid

from app.models.user import User
from app.services import language


def test_english_adds_nothing():
    """English is what the prompts are already written in — an extra
    instruction would spend tokens and nudge the register for most users."""
    assert language.directive("English") == ""
    assert language.directive("english") == ""
    assert language.directive("  ") == ""
    assert language.directive(None) == ""


def test_a_chosen_language_is_named_in_the_directive():
    out = language.directive("Hindi")
    assert "Hindi" in out
    # The person in front of you outranks the setting.
    assert "language they used" in out
    # Helplines are proper nouns and digits — they must survive translation.
    assert "phone numbers exactly" in out


def test_unlisted_languages_pass_through():
    """The field is free text server-side; a user knows their own language
    better than an allow-list does."""
    assert "Marathi" in language.directive("Marathi")


def test_for_user_reads_the_profile():
    assert language.for_user(User(email="a@b.c", language="Tamil")).find("Tamil") > 0
    assert language.for_user(User(email="a@b.c", language="English")) == ""


async def test_chat_reply_prompt_carries_the_language(client, monkeypatch):
    """The directive has to reach the model, not just exist."""
    seen: dict = {}

    async def fake_complete(system, prompt, max_tokens=1024):
        seen["system"] = system
        return "ठीक है।"

    from app.services import ai

    monkeypatch.setattr(ai, "complete", fake_complete)

    addr = f"lang-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "L"}
    )
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    await client.patch("/users/me", json={"language": "Hindi"})

    sent = await client.post("/chat/messages", json={"text": "hello"})
    assert sent.status_code == 201
    assert "Respond in Hindi" in seen["system"]


async def test_english_user_gets_an_unchanged_prompt(client, monkeypatch):
    seen: dict = {}

    async def fake_complete(system, prompt, max_tokens=1024):
        seen["system"] = system
        return "Okay."

    from app.services import ai

    monkeypatch.setattr(ai, "complete", fake_complete)

    addr = f"lang-en-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "L"}
    )
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

    await client.post("/chat/messages", json={"text": "hello"})
    assert "Respond in" not in seen["system"]


async def test_crisis_resources_survive_the_language_switch(client, monkeypatch):
    """A Hindi reply must still carry real, dialable help. The hotline block is
    appended AFTER the model, so it cannot be translated away."""
    async def fake_complete(system, prompt, max_tokens=1024):
        return "मैं यहाँ हूँ।"

    from app.services import ai

    monkeypatch.setattr(ai, "complete", fake_complete)

    addr = f"lang-crisis-{uuid.uuid4().hex[:8]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "L"}
    )
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    await client.patch("/users/me", json={"language": "Hindi", "region": "IN"})

    sent = await client.post("/chat/messages", json={"text": "I want to kill myself"})
    reply = sent.json()["reply"]["text"]
    assert "14416" in reply or "Tele-MANAS" in reply
