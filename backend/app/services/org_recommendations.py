"""AI recommendations for organisation admins — counts-only, by design.

Audit J#3, implemented as its option (a). The sibling's `hr_recommendations`
synthesises per-team *stress and engagement* — wellbeing-derived aggregates
this codebase deliberately refuses to create: `models/organization.py` states,
and `test_org.py` asserts, that the org module has NO wellbeing read paths at
all. That boundary is the product's public promise, and widening it is an
owner decision this feature does not make.

What survives the boundary is still useful: recommendations computed over the
aggregates the portal already may see — eligibility and activation counts per
group, suppression already applied at our threshold (floor 20, vs their 5).
"Group X activation is 12% against 64% elsewhere — consider a comms push" is
honest, actionable, and derived from nothing a member did inside the product.

The part of the sibling worth porting regardless is its load-bearing habit:
**sanitize before the prompt is built**. `_sanitize` drops every suppressed
group before any LLM payload exists, and the test asserts it at the prompt
boundary — an LLM cannot leak a number it was never shown.

On-demand and unstored: no scheduler, no table, no retention question. The
response carries the same honest `source` ("ai" | "rule") the plans do.
"""
from __future__ import annotations

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.organization import Organization
from app.services import ai, prompts
from app.services.organizations import GroupTotals, all_group_totals

logger = logging.getLogger("cerebro.org_recommendations")

#: Counts-only recommendations never justify alarm language. Two tiers, no
#: "urgent" — an activation number is not an emergency.
PRIORITIES = ("advisory", "informational")
MAX_RECOMMENDATIONS = 3

ORG_RECS_PROMPT = prompts.register(
    "org_recommendations",
    "You advise an organisation's benefits administrator for a mental-wellness "
    "benefit. You see ONLY per-group counts: eligible seats, activated members, "
    "and the activation percentage. You see nothing about usage, mood, or any "
    "individual, and you must not speculate about wellbeing, stress, or mental "
    "health of any group. Suggest practical administrative moves only "
    "(communications, onboarding sessions, seat reallocation, eligibility "
    "review). Return JSON: {\"recommendations\": [{\"title\": str (<=70 chars), "
    "\"body\": str (<=240 chars), \"priority\": \"advisory\"|\"informational\"}]}. "
    "1 to 3 items, most useful first. If the numbers suggest nothing, return "
    "fewer items rather than padding.",
)


def _sanitize(groups: list[GroupTotals]) -> list[dict]:
    """Only unsuppressed groups reach any prompt. The suppression decision was
    already made by `group_totals` at the organisation's threshold; this strips
    the suppressed rows entirely rather than passing nulls the model could
    remark on ("one group is hidden" is itself a disclosure)."""
    out = []
    for g in groups:
        if g.suppressed or g.activated is None:
            continue
        pct = round(100 * g.activated / g.eligible) if g.eligible else 0
        out.append({"group": g.name, "eligible": g.eligible, "activated": g.activated, "activation_pct": pct})
    return out


def _fallback(rows: list[dict]) -> list[dict]:
    """Deterministic recommendations when the LLM is off — never an empty
    dashboard on a sweep failure (the sibling's rule, kept)."""
    if not rows:
        return [{
            "title": "No groups are large enough to report yet",
            "body": "Counts appear once a group clears the reporting threshold. "
                    "Consider consolidating small eligibility groups.",
            "priority": "informational",
        }]
    avg = sum(r["activation_pct"] for r in rows) / len(rows)
    lowest = min(rows, key=lambda r: r["activation_pct"])
    recs = []
    if lowest["activation_pct"] < avg - 15:
        recs.append({
            "title": f"Activation lags in {lowest['group']}",
            "body": f"{lowest['activation_pct']}% of eligible people have activated, against "
                    f"{round(avg)}% across reportable groups. A targeted reminder or an "
                    "onboarding session usually closes this kind of gap.",
            "priority": "advisory",
        })
    recs.append({
        "title": "Overall activation",
        "body": f"{round(avg)}% of eligible seats are activated across reportable groups.",
        "priority": "informational",
    })
    return recs[:MAX_RECOMMENDATIONS]


def _shape(item: object) -> dict | None:
    if not isinstance(item, dict) or not str(item.get("title", "")).strip():
        return None
    priority = str(item.get("priority", "informational")).lower()
    return {
        "title": str(item["title"])[:70],
        "body": str(item.get("body", ""))[:240],
        "priority": priority if priority in PRIORITIES else "informational",
    }


async def recommend(db: AsyncSession, org: Organization) -> dict:
    """1–3 counts-only recommendations for this organisation's admins."""
    rows = _sanitize(await all_group_totals(db, org))

    if rows:
        system = await prompts.get("org_recommendations", db)
        payload = "\n".join(
            f"- {r['group']}: eligible {r['eligible']}, activated {r['activated']} ({r['activation_pct']}%)"
            for r in rows
        )
        result = await ai.complete_json(system, payload, max_tokens=500)
        if isinstance(result, dict) and isinstance(result.get("recommendations"), list):
            shaped = [s for s in (_shape(i) for i in result["recommendations"]) if s]
            if shaped:
                return {"source": "ai", "recommendations": shaped[:MAX_RECOMMENDATIONS]}

    return {"source": "rule", "recommendations": _fallback(rows)}
