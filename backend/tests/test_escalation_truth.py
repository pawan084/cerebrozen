"""Does the crisis record say what actually happened?

`escalation.on_crisis` alerts ops and, with consent, notifies a trusted contact.
Both senders swallow their own failures by design — a Twilio rejection must not
500 the message that triggered the scan, and that part is right. What was wrong
is what came next: `event.escalated = True` ran unconditionally afterwards, so a
contact who was never reached was recorded exactly like one who was.

The flag reads the same either way, which is what makes it dangerous rather than
merely wrong. Nothing surfaces it yet, so nobody has been misled — but the queue
now shows it, and the first person to trust it would have been trusting a guess.

"Safety never blocks" still holds throughout: every test here also asserts the
call returns rather than raising, because a delivery failure must never become
the user's problem.
"""

from __future__ import annotations

import uuid

import pytest
from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.safety import SafetyEvent
from app.models.trusted_contact import TrustedContact
from app.models.user import User
from app.services import email as email_service
from app.services import escalation
from app.services import sms as sms_service


async def _user_with_contact(
    *, method: str = "sms", consent: bool = True, value: str = "+911234567890"
) -> User:
    async with SessionLocal() as db:
        u = User(
            email=f"crisis-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password="x",
            name="Ann",
        )
        db.add(u)
        await db.flush()
        if value:
            db.add(
                TrustedContact(
                    user_id=u.id,
                    name="Kin",
                    method=method,
                    value=value,
                    relationship="friend",
                    notify_consent=consent,
                )
            )
        await db.commit()
        await db.refresh(u)
        return u


async def _fire(user: User) -> SafetyEvent:
    """Run one crisis escalation and hand back the event it recorded on."""
    async with SessionLocal() as db:
        event = SafetyEvent(
            user_id=user.id,
            source="chat",
            risk_level="crisis",
            reason="matched phrase",
            excerpt="…",
        )
        db.add(event)
        await db.flush()
        await escalation.on_crisis(db, user_id=user.id, event=event)
        await db.commit()
        return await db.scalar(select(SafetyEvent).where(SafetyEvent.id == event.id))


class TestASuccessfulReachIsRecordedAsOne:
    @pytest.mark.asyncio
    async def test_a_notified_contact_sets_the_flag(self):
        # Under TESTING the senders deliver to an outbox and report success.
        sms_service.sent_outbox.clear()
        user = await _user_with_contact()
        event = await _fire(user)

        assert event.escalated is True
        assert event.escalated_at is not None
        assert "contact_notified" in event.escalation_note
        assert sms_service.sent_outbox, "nothing was actually sent"


class TestAFailedReachIsNotRecordedAsSuccess:
    @pytest.mark.asyncio
    async def test_a_rejected_sms_leaves_the_flag_false(self, monkeypatch):
        """The bug, directly.

        Twilio answering 400 logs a warning and returns. Before this, the very
        next statement set `escalated = True` — so the record said a person had
        been reached about somebody's crisis when nobody had.
        """
        async def _refused(to, body):
            return False

        monkeypatch.setattr(sms_service, "send_sms", _refused)
        user = await _user_with_contact()
        event = await _fire(user)

        assert event.escalated is False, (
            "an unreachable contact was recorded as escalated"
        )
        assert event.escalated_at is None
        assert "contact_notify_failed" in event.escalation_note

    @pytest.mark.asyncio
    async def test_a_bounced_email_contact_is_the_same(self, monkeypatch):
        async def _refused(to, subject, body):
            return False

        monkeypatch.setattr(email_service, "send_email", _refused)
        user = await _user_with_contact(method="email", value="kin@test.app")
        event = await _fire(user)

        assert event.escalated is False
        assert "contact_notify_failed" in event.escalation_note

    @pytest.mark.asyncio
    async def test_the_transport_swallows_its_own_failure(self, monkeypatch):
        """Safety never blocks — including when the escalation itself fails.

        `on_crisis` runs inside the request that scanned the message, and it
        does NOT wrap the senders in a try. It does not need to, because each
        sender promises never to raise — but that promise is the thing keeping
        somebody's worst sentence from becoming a 500, so it is tested at the
        sender rather than assumed at the caller.

        Written this way after the first version monkeypatched httpx and proved
        nothing: under TESTING the sender returns at its outbox branch long
        before any HTTP client is touched.
        """
        monkeypatch.setenv("TESTING", "0")
        monkeypatch.setattr(sms_service, "_configured", lambda: True)

        class _Exploding:
            def __init__(self, *a, **kw):
                pass

            async def __aenter__(self):
                raise RuntimeError("twilio unreachable")

            async def __aexit__(self, *a):
                return False

        monkeypatch.setattr(sms_service.httpx, "AsyncClient", _Exploding)
        # Returns rather than raises, and says it did not land.
        assert await sms_service.send_sms("+911234567890", "hi") is False


    @pytest.mark.asyncio
    async def test_a_provider_rejection_is_reported_as_a_failure(self, monkeypatch):
        """A 400 from Twilio is the motivating case for all of this.

        Added after a mutation sweep: deleting the `return False` on the
        rejection branch broke NO test. Everything above exercised the
        exception path and the outbox path, so the one failure mode actually
        named in the docstrings — the provider accepting the request and
        refusing the message — was the one nobody checked.
        """
        monkeypatch.setenv("TESTING", "0")
        monkeypatch.setattr(sms_service, "_configured", lambda: True)

        class _Rejecting:
            def __init__(self, *a, **kw):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *a):
                return False

            async def post(self, url, data=None, auth=None):
                class _Resp:
                    status_code = 400
                    text = "The 'To' number is not a valid phone number."
                return _Resp()

        monkeypatch.setattr(sms_service.httpx, "AsyncClient", _Rejecting)
        assert await sms_service.send_sms("+91123", "hi") is False

    @pytest.mark.asyncio
    async def test_an_smtp_failure_is_reported_as_a_failure(self, monkeypatch):
        """The same hole on the email side, closed at the same time."""
        monkeypatch.setenv("TESTING", "0")
        monkeypatch.setattr(email_service.settings, "smtp_host", "smtp.invalid", raising=False)

        def _explode(to, subject, body):
            raise OSError("connection refused")

        monkeypatch.setattr(email_service, "_smtp_send", _explode)
        assert await email_service.send_email("a@test.app", "s", "b") is False

    @pytest.mark.asyncio
    async def test_an_unconfigured_sender_does_not_claim_delivery(self, monkeypatch):
        """No SMTP means nobody was reached. That the deployment cannot send is
        the explanation, not an exemption — and recording it as a success would
        make every dev and staging environment look like it escalated."""
        monkeypatch.setenv("TESTING", "0")
        monkeypatch.setattr(email_service.settings, "smtp_host", "", raising=False)
        assert await email_service.send_email("a@test.app", "s", "b") is False

        monkeypatch.setattr(sms_service, "_configured", lambda: False)
        assert await sms_service.send_sms("+91123", "hi") is False


class TestTheReasonNobodyWasReachedIsDistinguishable:
    """"Not reached" has several causes and they are not the same finding.

    A withheld consent is the product working. A failed send is an incident. A
    missing contact is neither. Collapsing them into one empty flag would leave
    a reviewer unable to tell which they are looking at.
    """

    @pytest.mark.asyncio
    async def test_a_user_who_withheld_consent_is_not_a_failure(self):
        user = await _user_with_contact(consent=False)
        event = await _fire(user)
        assert event.escalated is False
        assert "contact_not_consented" in event.escalation_note
        assert "failed" not in event.escalation_note

    @pytest.mark.asyncio
    async def test_no_contact_at_all_says_so(self):
        user = await _user_with_contact(value="")
        event = await _fire(user)
        assert "no_contact" in event.escalation_note
        assert "failed" not in event.escalation_note

    @pytest.mark.asyncio
    async def test_an_unconfigured_ops_alert_is_recorded_rather_than_inferred(
        self, monkeypatch
    ):
        """Nobody was told, and the reason is configuration.

        Left blank, a reviewer cannot tell "the alert went" from "there is no
        alert address set", and those call for different actions.
        """
        monkeypatch.setattr(escalation.settings, "ops_alert_email", "", raising=False)
        user = await _user_with_contact()
        event = await _fire(user)
        assert "ops_alert_unconfigured" in event.escalation_note

    @pytest.mark.asyncio
    async def test_a_configured_ops_alert_is_recorded_as_sent(self, monkeypatch):
        monkeypatch.setattr(
            escalation.settings, "ops_alert_email", "ops@test.app", raising=False
        )
        user = await _user_with_contact()
        event = await _fire(user)
        assert "ops_alerted" in event.escalation_note


class TestTheQueueShowsIt:
    @pytest.mark.asyncio
    async def test_a_reviewer_can_see_the_contact_was_not_reached(
        self, admin_client, monkeypatch
    ):
        """The whole point of recording it.

        A triage surface that shows a crisis event but not whether anybody was
        reached invites the reviewer to assume the system handled it.
        """
        async def _refused(to, body):
            return False

        monkeypatch.setattr(sms_service, "send_sms", _refused)
        user = await _user_with_contact()
        event = await _fire(user)

        rows = (await admin_client.get("/admin/safety")).json()
        mine = [r for r in rows if r["id"] == str(event.id)]
        assert mine, "the event is missing from the queue"
        assert mine[0]["escalated"] is False
        assert "contact_notify_failed" in mine[0]["escalation_note"]
