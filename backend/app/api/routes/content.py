from fastapi import APIRouter, Depends
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_optional_user
from app.models.content import ContentItem
from app.models.user import User
from app.schemas.content_data import ContentOut
from app.services import media
from app.services.textsearch import escape_like

router = APIRouter(prefix="/content", tags=["content"])


def _serialize(item: ContentItem, user: User | None) -> ContentOut:
    """Render one catalogue row for this caller.

    ``audio_url`` is replaced rather than passed through: the stored value is a
    bare path, and what a client should receive is a signed, expiring grant —
    or nothing at all, if they aren't entitled to this item's narration. The
    override is applied to the response model, never to the ORM row, so the
    session can't flush a token into the database.
    """
    return ContentOut.model_validate(item).model_copy(
        update={"audio_url": media.playback_url(item, user)}
    )


@router.get("", response_model=list[ContentOut])
async def list_content(
    kind: str | None = None,
    q: str | None = None,
    db: AsyncSession = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    """Public catalogue (published items), filterable by kind + search query.

    Stays readable without a token — a signed-out browser still gets titles,
    artwork and free narration. A recognised paid caller additionally gets
    playable URLs for premium items.
    """
    stmt = select(ContentItem).where(ContentItem.published.is_(True))
    if kind:
        stmt = stmt.where(ContentItem.kind == kind)
    if q:
        # Escaped like journal search always was (register C87): "100%" means
        # the characters, not "match everything".
        like = f"%{escape_like(q)}%"
        stmt = stmt.where(or_(
            ContentItem.title.ilike(like, escape="\\"),
            ContentItem.subtitle.ilike(like, escape="\\"),
        ))
    rows = await db.scalars(stmt.order_by(ContentItem.title))
    return [_serialize(item, user) for item in rows.all()]
