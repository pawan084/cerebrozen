"""Personal safety plan — user-authored, versioned, never a gate.

Three rules this module exists to keep:

1. **The user writes it.** There is no path here for the model to persist a
   plan. A client may pre-fill a suggestion, but what arrives in a PUT is what
   the user chose to keep.
2. **Nothing here blocks anything.** A missing, empty or stale plan must never
   change what a crisis reply does. This is an aid the user prepared for
   themselves, not a precondition.
3. **Editing never destroys the last version.** Superseding archives; history
   stays readable. Someone editing while distressed must not be able to lose
   what they wrote while well.
"""
from __future__ import annotations

from html import escape

from fastapi import APIRouter, Depends, HTTPException, Response, status
from fastapi.responses import HTMLResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.models.safety_plan import SafetyPlan
from app.models.user import User
from app.schemas.content_data import SafetyPlanOut, SafetyPlanUpdate

router = APIRouter(prefix="/safety-plan", tags=["safety"])

# Section headings, in the order the user meets them. Kept here rather than in
# the model so the wording is a presentation choice, not a schema change.
SECTION_LABELS = {
    "warning_signs": "Warning signs I notice",
    "internal_coping": "Things I can do on my own",
    "social_distractors": "People and places that take my mind off it",
    "social_support": "People I can ask for help",
    "professionals": "Professionals and services I can contact",
    "means_safety": "Making my space safer",
    "notes": "Anything else",
}


async def _live_plan(db: AsyncSession, user: User) -> SafetyPlan | None:
    return await db.scalar(
        select(SafetyPlan)
        .where(SafetyPlan.user_id == user.id, SafetyPlan.archived_at.is_(None))
        .order_by(SafetyPlan.version.desc())
    )


@router.get("/me", response_model=SafetyPlanOut | None)
async def get_my_plan(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """The live plan, or null when there isn't one yet.

    Null is a normal state, not an error — clients render the invitation to
    write one and must stay usable either way.
    """
    return await _live_plan(db, user)


@router.put("/me", response_model=SafetyPlanOut)
async def upsert_my_plan(
    payload: SafetyPlanUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Save the plan. The first save creates version 1; each later save
    archives the previous version and writes the next one.

    Fields left unset carry over from the live plan, so a client can save one
    section at a time through the guided flow without blanking the rest.

    An explicit `null` is NOT the same as unset: it is a request to clear that
    section, and it is honoured (stored as "" — the column default — and
    versioned like any other edit). Only omission carries the old value over.
    """
    current = await _live_plan(db, user)
    changes = payload.model_dump(exclude_unset=True)

    merged = {
        field: changes.get(field, getattr(current, field, "") if current else "")
        for field in (*SafetyPlan.SECTIONS, "notes")
    }

    if current is not None:
        # No-op saves must not spawn versions — a guided flow re-saves often.
        if all(merged[f] == getattr(current, f) for f in merged):
            return current
        current.archived_at = utcnow()

    plan = SafetyPlan(
        user_id=user.id,
        version=(current.version + 1) if current else 1,
        **merged,
    )
    db.add(plan)
    await db.commit()
    await db.refresh(plan)
    return plan


@router.get("/me/history", response_model=list[SafetyPlanOut])
async def my_plan_history(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Every version, newest first — the live one included."""
    return (
        await db.scalars(
            select(SafetyPlan)
            .where(SafetyPlan.user_id == user.id)
            .order_by(SafetyPlan.version.desc())
        )
    ).all()


@router.get("/me/printable", response_class=HTMLResponse)
async def printable_plan(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """The live plan as a self-contained, print-ready page.

    Deliberately HTML rather than a server-rendered PDF. A PDF library is a
    heavy dependency for one screen (weasyprint needs cairo/pango in the Alpine
    image; reportlab means hand-laying-out text), while every platform that
    matters already prints HTML to PDF for free — the browser's print dialog,
    iOS `UIPrintInteractionController`, Android `PrintManager`. It also stays
    readable if it is simply saved to a file, which matters more here than
    pagination fidelity: this is a page someone may need offline, in a hurry.

    No CSS/JS is fetched and none is inline-executable: the plan is text the
    user wrote, so every field is escaped and the response carries a CSP that
    forbids scripts outright.
    """
    plan = await _live_plan(db, user)
    if plan is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="No safety plan yet."
        )

    def block(heading: str, value: str) -> str:
        body = escape(value).strip().replace("\n", "<br>")
        if not body:
            body = '<span class="empty">— not filled in —</span>'
        return f"<section><h2>{escape(heading)}</h2><p>{body}</p></section>"

    sections = "".join(
        block(label, getattr(plan, field))
        for field, label in SECTION_LABELS.items()
        if field != "notes" or getattr(plan, field).strip()
    )
    html = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>My safety plan</title>
<style>
  body {{ font: 16px/1.6 Georgia, serif; color: #1c1740; max-width: 42rem;
         margin: 2rem auto; padding: 0 1rem; }}
  h1 {{ font-size: 1.6rem; margin-bottom: .25rem; }}
  h2 {{ font-size: 1rem; text-transform: uppercase; letter-spacing: .06em;
        color: #5b4fa8; margin: 1.6rem 0 .3rem; }}
  p {{ margin: 0; white-space: pre-wrap; }}
  .empty {{ color: #8a86a8; font-style: italic; }}
  .meta, footer {{ color: #6b6790; font-size: .8rem; }}
  footer {{ margin-top: 2.5rem; border-top: 1px solid #ddd; padding-top: .8rem; }}
  @media print {{ body {{ margin: 0; }} }}
</style></head>
<body>
  <h1>My safety plan</h1>
  <p class="meta">Version {plan.version} · saved {plan.created_at:%d %B %Y}</p>
  {sections}
  <footer>Written by you, in your own words. CereBro is wellness support — not
  therapy, diagnosis, or a crisis service. In immediate danger, contact your
  local emergency number.</footer>
</body></html>"""
    return HTMLResponse(
        content=html,
        headers={
            # The body is user-authored text. Escaped above; scripts forbidden
            # here as well, so a crafted plan can never execute on the API origin.
            "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'",
            "X-Content-Type-Options": "nosniff",
        },
    )


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_plan(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Delete the plan and its whole history.

    A real delete, not an archive: this is the user's own crisis material and
    "delete" has to mean it. Idempotent.
    """
    rows = (
        await db.scalars(select(SafetyPlan).where(SafetyPlan.user_id == user.id))
    ).all()
    for row in rows:
        await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
