"""What an error report is allowed to contain (WC-17).

The value of this module is entirely in what it REFUSES to send. A test that
only checked "the event was dispatched" would pass just as happily on a version
that shipped the journal entry that caused the crash, so most of these assert
absence — and they assert it against the whole serialised payload rather than
against the field the leak was expected in, because a leak arrives in the field
nobody thought of.
"""

from __future__ import annotations

import json

import pytest

from app.services import errors


@pytest.fixture(autouse=True)
def _isolated_sinks():
    """Every case starts log-only and leaves nothing registered behind."""
    errors.reset_sinks()
    yield
    errors.reset_sinks()


class Collector:
    """A sink that keeps what it was handed, so a test can read it."""

    def __init__(self) -> None:
        self.events: list[errors.ErrorEvent] = []

    def send(self, event: errors.ErrorEvent) -> None:
        self.events.append(event)


def _boom(secret: str) -> None:
    """Raise with the secret in the message AND in a local — the two ways a
    reporter usually leaks: `str(exc)` and frame locals."""
    private_note = secret  # noqa: F841 - deliberately a local, to prove it stays one
    raise ValueError(f"invalid entry: {secret}")


def capture_boom(secret: str = "I have been feeling hopeless since March") -> tuple[errors.ErrorEvent, str]:
    collector = Collector()
    errors.register_sink(collector)
    try:
        _boom(secret)
    except ValueError as exc:
        errors.capture(exc, where="POST /journal", request_id="req-1", user_id="u-42")
    assert collector.events, "the event never reached the sink"
    event = collector.events[0]
    return event, json.dumps(event.as_dict(), sort_keys=True)


class TestNothingSensitiveTravels:
    def test_the_exception_message_is_never_sent(self):
        # asyncpg, pydantic and sqlalchemy all quote the offending VALUE in the
        # message. On this product that value is a person's sentence.
        _, payload = capture_boom()
        assert "hopeless" not in payload
        assert "invalid entry" not in payload

    def test_the_type_is_sent_because_it_carries_no_content(self):
        event, _ = capture_boom()
        assert event.kind == "ValueError"

    def test_frame_locals_do_not_travel_with_the_frames(self):
        # The frames are kept — they are how anyone finds the bug — but a
        # reporter that also shipped `f_locals` would ship `private_note`.
        event, payload = capture_boom()
        assert event.frames, "frames are the useful half; they should be here"
        assert "private_note" not in payload
        assert "hopeless" not in payload

    def test_a_frame_is_a_position_and_nothing_else(self):
        event, _ = capture_boom()
        innermost = event.frames[-1]
        assert " in _boom" in innermost
        assert "errors.py" not in innermost, "the reporter should not blame itself"

    @pytest.mark.parametrize(
        "secret",
        [
            "someone@example.com",
            "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def",
            "I want to die",
            "+91 98765 43210",
        ],
    )
    def test_no_shape_of_secret_gets_through(self, secret):
        # Parametrised over the four kinds this codebase actually handles: an
        # address, a token, a crisis phrase, a phone number.
        _, payload = capture_boom(secret)
        assert secret not in payload


class TestIdentifiersInPathsBecomeStructure:
    @pytest.mark.parametrize(
        "raw,expected",
        [
            ("/journal/2f1c9b0e-7a53-4a1e-9c8f-0d2b6a4e11aa", "/journal/{id}"),
            ("/journal/1421", "/journal/{id}"),
            ("/users/me/consent", "/users/me/consent"),
            ("/media/catalog", "/media/catalog"),
            ("/moods/8f3a2b1c9d0e4f5a6b7c8d9e0f1a2b3c", "/moods/{id}"),
            ("/", "/"),
        ],
    )
    def test_a_path_keeps_its_route_and_loses_its_ids(self, raw, expected):
        assert errors.template_path(raw) == expected

    def test_the_matched_route_template_wins_over_the_raw_path(self):
        # FastAPI's template is id-free by construction, so it is preferred and
        # the regex fallback never has to be clever.
        where = errors.where_for_request("GET", "/journal/{entry_id}", "/journal/2f1c9b0e")
        assert where == "GET /journal/{entry_id}"

    def test_an_unrouted_request_still_loses_its_ids(self):
        # 404s and failures before routing have no template; the fallback runs.
        where = errors.where_for_request("GET", None, "/journal/1421")
        assert where == "GET /journal/{id}"


class TestCountingPeopleWithoutNamingThem:
    def test_a_user_id_is_never_sent_in_the_clear(self):
        event, payload = capture_boom()
        assert "u-42" not in payload
        assert event.user is not None

    def test_the_same_user_hashes_the_same_way_so_they_can_be_counted(self):
        assert errors.hash_user("u-42") == errors.hash_user("u-42")
        assert errors.hash_user("u-42") != errors.hash_user("u-43")

    def test_no_user_means_no_field_rather_than_an_empty_one(self):
        assert errors.hash_user(None) is None
        assert errors.hash_user("") is None

    def test_the_handle_is_too_short_to_be_an_identity(self):
        assert len(errors.hash_user("u-42")) == 12


class TestFingerprintsGroupTheSameFault:
    def _capture(self, message: str, where: str = "POST /journal") -> str:
        collector = Collector()
        errors.reset_sinks()
        errors.register_sink(collector)
        try:
            raise ValueError(message)
        except ValueError as exc:
            errors.capture(exc, where=where)
        return collector.events[0].fingerprint

    def test_the_same_fault_with_different_values_is_one_fingerprint(self):
        # The whole point of counting: 412 failures of one bug must not read as
        # 412 distinct bugs because each quoted a different user's input.
        assert self._capture("entry 1 broke") == self._capture("entry 2 broke")

    def test_the_same_fault_on_a_different_route_is_a_different_one(self):
        assert self._capture("x", "POST /journal") != self._capture("x", "POST /moods")


class TestItCannotMakeThingsWorse:
    def test_a_broken_sink_does_not_break_the_request(self):
        class Exploding:
            def send(self, event):
                raise RuntimeError("the tracker is down")

        errors.register_sink(Exploding())
        collector = Collector()
        errors.register_sink(collector)
        try:
            raise ValueError("boom")
        except ValueError as exc:
            errors.capture(exc, where="GET /health")   # must not raise
        assert collector.events, "a failing sink must not stop the healthy ones"

    def test_it_works_with_no_sink_configured_at_all(self):
        # The degrade-without-keys rule: with nothing registered but the log
        # sink, capture still returns a complete event.
        try:
            raise KeyError("k")
        except KeyError as exc:
            event = errors.capture(exc, where="job:nudge-dispatch")
        assert event.kind == "KeyError"
        assert event.where == "job:nudge-dispatch"
        assert event.fingerprint

    def test_an_exception_with_no_traceback_is_still_reportable(self):
        # Raised-and-caught is the normal path, but a bare exception object can
        # reach capture() from a callback; it must not crash on `__traceback__`.
        event = errors.capture(ValueError("never raised"), where="job:x")
        assert event.frames == ()
        assert event.fingerprint


class TestTheWiringAtTheRequestBoundary:
    """The unit tests above prove the policy; these prove it is actually reached.

    A capture() that is never called is a scrubber with nothing to scrub, and
    the middleware is the only place an unhandled request failure passes
    through.
    """

    @pytest.mark.asyncio
    async def test_an_unhandled_request_failure_is_captured(self, client):
        from app.main import app

        collector = Collector()
        errors.register_sink(collector)

        @app.get("/__boom/{entry_id}")
        async def _boom_route(entry_id: str):  # pragma: no cover - raises by design
            raise RuntimeError(f"exploded on {entry_id}")

        try:
            resp = await client.get("/__boom/2f1c9b0e-7a53-4a1e-9c8f-0d2b6a4e11aa")
            assert resp.status_code == 500
            # The caller still gets the correlation id it can quote to support.
            assert resp.json()["request_id"]

            assert collector.events, "the boundary never reported the failure"
            event = collector.events[0]
            assert event.kind == "RuntimeError"
            # FastAPI's own template, so the uuid in the URL never travels.
            assert event.where == "GET /__boom/{entry_id}"
            assert event.request_id == resp.json()["request_id"]
            payload = json.dumps(event.as_dict())
            assert "2f1c9b0e" not in payload
            assert "exploded" not in payload
        finally:
            app.router.routes = [
                r for r in app.router.routes
                if getattr(r, "path", None) != "/__boom/{entry_id}"
            ]
