import datetime as dt

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.sleep import SleepLog
from app.models.user import User
from app.schemas.content_data import SleepLogCreate, SleepLogOut, SleepSummaryOut
from app.services import nudges
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
    # max() floors a negative ?limit= (register C32).
    rows = await db.scalars(query.order_by(SleepLog.date.desc()).limit(max(1, min(limit, 366))))
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
    try:
        await db.flush()
    except IntegrityError:
        # Register C52: two concurrent saves of the same night both found no
        # row; the loser used to 500 on `uq_sleep_logs_user_date`. Adopt the
        # winner's row and apply this save as the edit it semantically is.
        await db.rollback()
        log = await db.scalar(
            select(SleepLog).where(SleepLog.user_id == user.id, SleepLog.date == payload.date)
        )
        if log is None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Try again")
        for field, value in payload.model_dump().items():
            setattr(log, field, value)
        response.status_code = status.HTTP_200_OK
    # Proactive: the diary's own bedtimes anchor tonight's wind-down reminder.
    await nudges.schedule_wind_down(db, user)
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


@router.delete("/{night}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_sleep(
    night: dt.date,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Delete the caller's diary entry identified by its wake-up date."""
    log = await db.scalar(
        select(SleepLog).where(SleepLog.user_id == user.id, SleepLog.date == night)
    )
    if log is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sleep entry not found")
    await db.delete(log)
    await db.commit()
