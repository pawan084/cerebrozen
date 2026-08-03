"""Importing this package registers every model on Base.metadata."""
from app.models.user import User
from app.models.consent import Consent
from app.models.deletion_ledger import DeletionLedger
from app.models.device_token import DeviceToken
from app.models.idempotency import IdempotencyRecord
from app.models.mood import MoodLog
from app.models.journal import JournalEntry
from app.models.agent_action import AgentAction
from app.models.chat import ChatMessage
from app.models.plan import Plan, PlanStep
from app.models.content import ContentItem
from app.models.nudge import Nudge
from app.models.habit import Goal, Habit, HabitCompletion
from app.models.insight import Insight
from app.models.intervention import InterventionRecommendation
from app.models.memory import ContextMemory
from app.models.oracle_audit import OracleToolCall
from app.models.safety import SafetyEvent
from app.models.safety_plan import SafetyPlan
from app.models.sleep import SleepLog
from app.models.trusted_contact import TrustedContact
from app.models.login_code import LoginCode
from app.models.media import MediaAsset
from app.models.product_event import ProductEvent
from app.models.program import ProgramEnrollment
from app.models.prompt import PromptTemplate
from app.models.recommendation import PracticeCatalog, Recommendation
from app.models.waitlist import WaitlistEntry
from app.models.web_push import WebPushSubscription
from app.models.webhook_event import ProcessedWebhook

__all__ = [
    "User",
    "Consent",
    "DeletionLedger",
    "DeviceToken",
    "IdempotencyRecord",
    "MoodLog",
    "JournalEntry",
    "AgentAction",
    "ChatMessage",
    "Plan",
    "PlanStep",
    "ContentItem",
    "Nudge",
    "Goal",
    "Habit",
    "HabitCompletion",
    "Insight",
    "InterventionRecommendation",
    "ContextMemory",
    "OracleToolCall",
    "SafetyEvent",
    "SafetyPlan",
    "SleepLog",
    "TrustedContact",
    "LoginCode",
    "MediaAsset",
    "ProductEvent",
    "ProgramEnrollment",
    "PromptTemplate",
    "PracticeCatalog",
    "Recommendation",
    "WaitlistEntry",
    "WebPushSubscription",
    "ProcessedWebhook",
]
