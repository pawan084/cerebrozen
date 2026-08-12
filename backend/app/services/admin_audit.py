"""Record what an operator did. One helper, called from every mutating
admin route, so the trail cannot be forgotten in one of them.

Register E34/E35. Two rules this keeps:

1. **Never raises.** An audit failure must not roll back an action that
   already happened, nor block one the operator is entitled to take. A
   swallowed write is logged loudly instead.
2. **Never carries user content.** The excerpt reveal records that an admin
   opened event X, never a word of what they read. `detail` is for the
   operational specifics an investigation needs (a prompt key and version, a
   broadcast's audience size), not for the data being administered.
"""
from __future__ import annotations

import logging
import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.admin_audit import AdminAuditLog
from app.models.user import User

logger = logging.getLogger("cerebro.admin.audit")


async def record(
    db: AsyncSession,
    admin: User | None,
    action: str,
    *,
    target_type: str = "",
    target_id: str | uuid.UUID = "",
    reason: str = "",
    detail: dict | None = None,
    org_id: uuid.UUID | None = None,
) -> None:
    """Append one operator action. Flushed with the caller's transaction so
    the record and the change it describes land together."""
    try:
        db.add(
            AdminAuditLog(
                admin_id=getattr(admin, "id", None),
                admin_email=getattr(admin, "email", "") or "",
                action=action,
                target_type=target_type,
                target_id=str(target_id or ""),
                reason=(reason or "")[:2000],
                detail=detail or {},
                org_id=org_id,
            )
        )
        await db.flush()
    except Exception:   # noqa: BLE001 - see rule 1 above
        logger.exception("admin audit write failed action=%s target=%s", action, target_id)
