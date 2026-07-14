"""Demo-request intake (public) and the ops pipeline view (internal).

The marketing site posts here (Phase 2 wiring); email delivery remains its
fallback. Honeypot semantics match the site's form."""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import require_internal_admin
from app.models import DemoRequest

router = APIRouter(tags=["demo"])


class DemoIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    email: EmailStr
    company: str = Field(min_length=1, max_length=200)
    size: str = Field(default="", max_length=40)
    message: str = Field(default="", max_length=4000)
    website: str = Field(default="", max_length=200)  # honeypot


@router.post("/demo-requests", status_code=201)
async def create_demo_request(body: DemoIn, session: AsyncSession = Depends(get_session)):
    if body.website.strip():
        # A filled honeypot is a bot: report success, store nothing.
        return {"ok": True}
    session.add(
        DemoRequest(
            name=body.name.strip(),
            email=body.email.lower().strip(),
            company=body.company.strip(),
            size=body.size.strip(),
            message=body.message.strip(),
        )
    )
    await session.commit()
    return {"ok": True}


@router.get("/admin/demo-requests", dependencies=[Depends(require_internal_admin)])
async def list_demo_requests(session: AsyncSession = Depends(get_session)):
    rows = (
        await session.execute(select(DemoRequest).order_by(DemoRequest.created_at.desc()))
    ).scalars().all()
    return [
        {
            "id": r.id,
            "name": r.name,
            "email": r.email,
            "company": r.company,
            "size": r.size,
            "message": r.message,
            "status": r.status,
            "created_at": str(r.created_at),
        }
        for r in rows
    ]


@router.patch("/admin/demo-requests/{request_id}", dependencies=[Depends(require_internal_admin)])
async def update_demo_request(
    request_id: str, body: dict, session: AsyncSession = Depends(get_session)
):
    row = (
        await session.execute(select(DemoRequest).where(DemoRequest.id == request_id))
    ).scalar_one_or_none()
    if row is None:
        raise HTTPException(404, "no such demo request")
    status = str(body.get("status", "")).strip()
    if status not in ("new", "contacted", "closed"):
        raise HTTPException(400, "status must be new, contacted, or closed")
    row.status = status
    await session.commit()
    return {"id": row.id, "status": row.status}
