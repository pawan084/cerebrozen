"""The imagery honesty pass has to reach rows that already exist.

2026-07-04 set ``seed._IMG = ""`` so clients render their branded symbol wells
instead of hotlinked stock photos. But seeding is additive by title, so the
change only ever applied to NEW rows: every database seeded before that date
kept serving the old URLs. Found on a device 2026-07-30 — Home's "For tonight"
rail showed the sleep story "Rain over quiet hills" as a sunlit desert canyon,
fetched from a third-party CDN with the user's IP attached.
"""
from sqlalchemy import select

from app import seed
from app.core.database import SessionLocal
from app.models.content import ContentItem


async def _titled(title: str) -> ContentItem | None:
    async with SessionLocal() as s:
        return await s.scalar(select(ContentItem).where(ContentItem.title == title))


async def test_seeded_stock_imagery_is_cleared_on_boot():
    async with SessionLocal() as s:
        s.add(ContentItem(
            title="Legacy stock row",
            subtitle="Sleep story · 18 min",
            kind="sleep",
            symbol="moon.stars",
            image_url="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format",
            duration_min=18,
        ))
        await s.commit()

    async with SessionLocal() as s:
        await seed.seed(s)
        await s.commit()

    row = await _titled("Legacy stock row")
    assert row is not None, "the backfill must not delete rows"
    assert row.image_url == "", "a hotlinked stock photo must not survive a boot"


async def test_admin_attached_art_is_never_clobbered():
    """The honesty pass bans stock hotlinks, not imagery. Licensed art an admin
    attached through the CMS is the whole point of keeping the column."""
    async with SessionLocal() as s:
        s.add(ContentItem(
            title="Curated art row",
            subtitle="Soundscape · 45 min",
            kind="soundscape",
            symbol="moon.zzz",
            image_url="https://cdn.cerebrozen.in/art/deep-night-drift.webp",
            duration_min=45,
        ))
        await s.commit()

    async with SessionLocal() as s:
        await seed.seed(s)
        await s.commit()

    row = await _titled("Curated art row")
    assert row.image_url == "https://cdn.cerebrozen.in/art/deep-night-drift.webp"


async def test_the_catalogue_itself_ships_no_stock_urls():
    assert seed._IMG == ""
