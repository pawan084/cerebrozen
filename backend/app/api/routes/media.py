from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.media import MediaAsset
from app.schemas.media import MediaAssetOut

# NOTE (route ordering): this router shares its "/media" prefix with the
# StaticFiles mount in app.main. That is safe *because* app.include_router runs
# before app.mount — an exact Route registered first wins over a later Mount. If
# the mount ever moves above the router, "/media/catalog" would be looked up as a
# file on disk and 404. test_media_catalog.py locks the current order.
router = APIRouter(prefix="/media", tags=["media"])


@router.get("/catalog", response_model=list[MediaAssetOut])
async def media_catalog(
    kind: str | None = None,
    db: AsyncSession = Depends(get_db),
):
    """Every published sound/video, keyed for client lookup.

    Public, like /content — the assets are decorative ambience, not user data, and
    the clients need them before sign-in (onboarding breathes). Rows with an empty
    `url` are returned on purpose: they tell the client which keys exist so it can
    play its bundled fallback for them and light up the moment an admin uploads one.
    """
    stmt = select(MediaAsset).where(MediaAsset.published.is_(True))
    if kind:
        stmt = stmt.where(MediaAsset.kind == kind)
    rows = await db.scalars(stmt.order_by(MediaAsset.key))
    return rows.all()
