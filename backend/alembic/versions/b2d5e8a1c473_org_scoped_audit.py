"""scope admin audit rows to an organisation

`admin_audit_logs` recorded CereBro operator actions only. The portal's AUD-01
screen promises "trace every administrative action", and nothing recorded what
an ORG administrator did — so the claim was false for the surface that made it.

Nullable: platform actions keep org_id NULL, which is what distinguishes them.
CASCADE, because an organisation's trail is meaningless once the organisation is
gone and keeping it would be retaining data about a customer we no longer serve.

Revision ID: b2d5e8a1c473
Revises: a1c4f7e2b930
Create Date: 2026-08-13 09:20:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "b2d5e8a1c473"
down_revision: Union[str, None] = "a1c4f7e2b930"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("admin_audit_logs", sa.Column("org_id", sa.Uuid(), nullable=True))
    op.create_foreign_key(
        "fk_admin_audit_logs_org_id",
        "admin_audit_logs",
        "organizations",
        ["org_id"],
        ["id"],
        ondelete="CASCADE",
    )
    op.create_index("ix_admin_audit_logs_org_id", "admin_audit_logs", ["org_id"])


def downgrade() -> None:
    op.drop_index("ix_admin_audit_logs_org_id", table_name="admin_audit_logs")
    op.drop_constraint("fk_admin_audit_logs_org_id", "admin_audit_logs", type_="foreignkey")
    op.drop_column("admin_audit_logs", "org_id")
