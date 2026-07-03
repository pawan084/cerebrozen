import datetime as dt

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.sleep import SleepLog
from app.models.user import User
from app.schemas.content_data import SleepLogCreate, SleepLogOut, SleepSummaryOut
from app.services import sleep as sleep_service

router = APIRouter(prefix="/sleep", tags=["sleep"])


@router.get("", response_model=list[SleepLogOut])
async def list_sleep(
    start: dt.date | None = None,
    end: dt.date | None = None,
    limit: int = 31,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(SleepLog).where(SleepLog.user_id == user.id)
    if start:
        query = query.where(SleepLog.date >= start)
    if end:
        query = query.where(SleepLog.date <= end)
    rows = await db.scalars(query.order_by(SleepLog.date.desc()).limit(min(limit, 366)))
    return rows.all()


@router.post("", response_model=SleepLogOut, status_code=201)
async def upsert_sleep(
    payload: SleepLogCreate,
    response: Response,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # One diary entry per night: re-submitting a date edits that morning's entry.
    log = await db.scalar(
        select(SleepLog).where(SleepLog.user_id == user.id, SleepLog.date == payload.date)
    )
    if log:
        for field, value in payload.model_dump().items():
            setattr(log, field, value)
        response.status_code = status.HTTP_200_OK
    else:
        log = SleepLog(user_id=user.id, **payload.model_dump())
        db.add(log)
    await db.flush()
    await db.commit()
    await db.refresh(log)
    return log


@router.get("/summary", response_model=SleepSummaryOut)
async def sleep_summary(
    days: int = 7,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await sleep_service.weekly_summary(db, user, days=max(2, min(days, 90)))
