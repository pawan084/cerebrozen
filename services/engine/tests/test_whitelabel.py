"""White-label contract: a second client must be a CONFIG change, not a fork.

Two things here are safety/legal, not cosmetics:
  - the crisis helpline must be settable per region (988 is US-only and useless abroad);
  - no real person may be named anywhere in the source.
"""
import pathlib
import re

import pytest


def test_brand_name_is_configurable(monkeypatch):
    """The coach's own identity must follow the brand, or the product cannot be resold
    without editing code."""
    monkeypatch.setenv("CEREBROZEN_BRAND_NAME", "Athena")
    import importlib

    from app import config
    from app.graph import guardrails

    importlib.reload(config)
    importlib.reload(guardrails)
    try:
        assert "Athena" in guardrails.IDENTITY["default"]
        assert "Athena" in guardrails.IDENTITY["CH"]
        assert "CereBroZen" not in guardrails.IDENTITY["default"]
    finally:
        monkeypatch.delenv("CEREBROZEN_BRAND_NAME", raising=False)
        importlib.reload(config)
        importlib.reload(guardrails)


def test_crisis_helpline_is_regional_not_hardcoded_to_the_us(monkeypatch):
    """988 is a US number. Shipping it to a user in India or the UK hands someone in
    crisis a line that does not answer — a safety defect, not a locale nit.

    Asserted against the reply the SAFE_RESPONSE NODE ACTUALLY SENDS. This test used to
    read a `prompts.SAFE_RESPONSE` constant, which stopped being the thing served the
    moment the reply became per-language — so a client could have set their helpline,
    watched this pass, and still shipped the old string to a user in crisis. Test the
    served path or don't bother.
    """
    monkeypatch.setenv("CEREBROZEN_CRISIS_LINE", "the Tele-MANAS helpline on 14416 (India)")
    import importlib

    from app.graph import crisis
    from app.llm import prompts

    importlib.reload(prompts)
    try:
        assert "14416" in crisis.safe_response("en")
        assert "988" not in crisis.safe_response("en")
        # And in every language we localise, or the helpline is only regional in English.
        assert "14416" in crisis.safe_response("es")
    finally:
        monkeypatch.delenv("CEREBROZEN_CRISIS_LINE", raising=False)
        importlib.reload(prompts)


def test_the_default_crisis_reply_names_no_country():
    """With nothing configured, the reply must still send the user somewhere REAL —
    an international directory that resolves to their own region — never a US number."""
    import importlib

    from app.graph import crisis
    from app.llm import prompts

    importlib.reload(prompts)
    reply = crisis.safe_response("en")
    assert "988" not in reply
    assert "findahelpline.com" in reply


def test_a_crisis_reply_never_comes_back_empty(monkeypatch):
    """Whatever else breaks, the person must get words. An unknown language, a corrupt
    override file, a template with a bad placeholder — every one of those paths falls back
    to English rather than to an empty string, because a blank screen is what a person in
    crisis least needs."""
    from app.graph import crisis

    assert crisis.safe_response("xx").strip()          # language we don't have
    assert crisis.safe_response("").strip()            # no language at all

    monkeypatch.setenv("CEREBROZEN_CRISIS_MESSAGES_FILE", "/nonexistent/path.json")
    assert crisis.safe_response("es").strip()          # unreadable override → still replies
    monkeypatch.delenv("CEREBROZEN_CRISIS_MESSAGES_FILE")


@pytest.mark.parametrize("name", ["Puja", "bibek", "mithilesh", "vikash", "romila"])
def test_no_real_person_is_named_in_the_source(name):
    """These were the previous client's QA staff, embedded in code comments and in the
    greeting few-shots. Shipping them to a new client is a GDPR problem, not a tidiness
    one. (Incident context was kept — the PEOPLE were anonymised.)"""
    root = pathlib.Path(__file__).resolve().parent.parent
    hits = []
    for f in list((root / "app").rglob("*.py")) + list((root / "scripts").rglob("*.py")):
        if re.search(rf"\b{name}\b", f.read_text(encoding="utf-8"), re.IGNORECASE):
            hits.append(str(f.relative_to(root)))
    assert not hits, f"real person '{name}' still named in: {hits}"


# ── the first tenant's real infrastructure must not be inheritable by accident ──

def test_the_incumbents_resource_names_are_known_and_enumerated():
    """These defaults name a Mongo database and an S3 bucket that REALLY EXIST and belong
    to the first client. They are deliberately not renamed — renaming a default does not
    rename the resource, it just points the app at a database that isn't there and orphans
    every checkpointed session (tried; reverted).

    So they must at least be *enumerated*, because an un-enumerated one is one that a
    second tenant inherits in silence.
    """
    from app import config

    assert set(config._TENANT_DEFAULTS) == {
        "MONGO_DB_BACKEND_DB",
        "RASA_DB",
        "MONGO_CHECKPOINT_DB",
        "RAG_S3_BUCKET",
    }


def test_strict_tenant_refuses_to_boot_on_an_incumbent_default(monkeypatch):
    """THE white-label guard. A second tenant who forgets MONGO_CHECKPOINT_DB writes their
    sessions into a database named after another client; one who forgets RAG_S3_BUCKET
    points retrieval at another client's bucket, which their AWS account cannot read — so
    every extraction returns null and the coach silently loses its concepts and learning
    aids. It degrades CLEANLY, with no error. Nobody notices.

    CEREBROZEN_STRICT_TENANT turns that silent inheritance into a startup crash.
    """
    import importlib

    monkeypatch.setenv("CEREBROZEN_STRICT_TENANT", "true")
    monkeypatch.delenv("MONGO_CHECKPOINT_DB", raising=False)
    monkeypatch.delenv("RAG_S3_BUCKET", raising=False)
    monkeypatch.delenv("S3_BUCKET_NAME", raising=False)

    from app import config

    with pytest.raises(RuntimeError, match="FIRST TENANT"):
        importlib.reload(config)

    # And it must name the offenders — an error that doesn't say WHICH var is a puzzle.
    monkeypatch.delenv("CEREBROZEN_STRICT_TENANT")
    importlib.reload(config)


def test_a_second_tenant_who_sets_everything_boots_clean(monkeypatch):
    """The guard must be satisfiable. If it isn't, the client's first move is to delete it."""
    import importlib

    monkeypatch.setenv("CEREBROZEN_STRICT_TENANT", "true")
    monkeypatch.setenv("MONGO_DB_BACKEND_DB", "acme_backend")
    monkeypatch.setenv("RASA_DB", "acme_rasa")
    monkeypatch.setenv("MONGO_CHECKPOINT_DB", "acme_langgraph")
    monkeypatch.setenv("RAG_S3_BUCKET", "acme-rag-data")

    from app import config

    importlib.reload(config)
    try:
        assert config.tenant_values_at_incumbent_default() == []
    finally:
        for var in ("CEREBROZEN_STRICT_TENANT", "MONGO_DB_BACKEND_DB", "RASA_DB",
                    "MONGO_CHECKPOINT_DB", "RAG_S3_BUCKET"):
            monkeypatch.delenv(var, raising=False)
        importlib.reload(config)


def test_no_client_domain_or_internal_document_is_named_in_the_source():
    """A client's production domain and their internal BRD filename are their paper trail,
    not ours. Shipping either to a second client leaks the first one's existence."""
    root = pathlib.Path(__file__).resolve().parent.parent
    offenders = []
    for path in list(root.glob("app/**/*.py")) + list(root.glob("scripts/*.py")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for needle in ("cerebrozensupercoachapi.com", "RepeatUserCheckin_BRD"):
            if needle in text:
                offenders.append(f"{path.relative_to(root)}: {needle}")
    assert not offenders, f"first client's identifiers still in source: {offenders}"
