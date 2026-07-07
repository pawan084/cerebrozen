from __future__ import annotations

from sqlalchemy import Boolean, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class PromptTemplate(Base):
    """One immutable version of a registered LLM prompt.

    Every edit inserts a new (name, version) row; exactly one version per name
    is ``active`` and overrides the in-code default (services/prompts.py).
    Deactivating every row reverts the prompt to its code default — prompts can
    always roll back without a deploy, and the code default is always a safe
    floor (CI/dev run with an empty table).
    """

    __tablename__ = "prompt_templates"
    __table_args__ = (UniqueConstraint("name", "version", name="uq_prompt_name_version"),)

    name: Mapped[str] = mapped_column(String(80), index=True)
    version: Mapped[int] = mapped_column(Integer, default=1)
    template: Mapped[str] = mapped_column(Text)
    notes: Mapped[str] = mapped_column(String(255), default="")
    active: Mapped[bool] = mapped_column(Boolean, default=True)
