"""Importing this package registers every model on Base.metadata."""
from app.models.user import User
from app.models.consent import Consent
from app.models.deletion_ledger import DeletionLedger
from app.models.mood import MoodLog
from app.models.journal import JournalEntry
from app.models.chat import ChatMessage
from app.models.plan import Plan, PlanStep
from app.models.content import ContentItem
from app.models.nudge import Nudge
from app.models.insight import Insight
from app.models.safety import SafetyEvent
from app.models.sleep import SleepLog
from app.models.trusted_contact import TrustedContact
from app.models.login_code import LoginCode
from app.models.product_event import ProductEvent
from app.models.prompt import PromptTemplate
from app.models.waitlist import WaitlistEntry
from app.models.web_push import WebPushSubscription

__all__ = [
    "User",
    "Consent",
    "DeletionLedger",
    "MoodLog",
    "JournalEntry",
    "ChatMessage",
    "Plan",
    "PlanStep",
    "ContentItem",
    "Nudge",
    "Insight",
    "SafetyEvent",
    "SleepLog",
    "TrustedContact",
    "LoginCode",
    "ProductEvent",
    "PromptTemplate",
    "WaitlistEntry",
    "WebPushSubscription",
]
