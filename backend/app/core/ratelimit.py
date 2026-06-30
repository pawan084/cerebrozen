"""Shared rate limiter (slowapi).

Applied to auth endpoints to blunt credential-stuffing / brute force. Disabled
under the test suite, where many sign-ups come from a single client and would
otherwise trip the limit.
"""
import os

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address, enabled=os.getenv("TESTING") != "1")
