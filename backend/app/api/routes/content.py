from fastapi import APIRouter, Depends
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.content import ContentItem
from app.schemas.content_data import ContentOut

router = APIRouter(prefix="/content", tags=["content"])


@router.get("", response_model=list[ContentOut])
async def list_content(
    kind: str | None = None,
    q: str | None = None,
    db: AsyncSession = Depends(get_db),
):
    """Public catalogue (published items), filterable by kind + search query."""
    stmt = select(ContentItem).where(ContentItem.published.is_(True))
    if kind:
        stmt = stmt.where(ContentItem.kind == kind)
    if q:
        like = f"%{q}%"
        stmt = stmt.where(or_(ContentItem.title.ilike(like), ContentItem.subtitle.ilike(like)))
    rows = await db.scalars(stmt.order_by(ContentItem.title))
    return rows.all()
