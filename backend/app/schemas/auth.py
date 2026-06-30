from pydantic import BaseModel, EmailStr, Field


class SignupRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    name: str = Field(default="", max_length=120)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class AppleSignInRequest(BaseModel):
    """The identity token from Sign in with Apple, plus the display name Apple
    only provides on the very first authorization (optional thereafter)."""
    identity_token: str
    name: str = Field(default="", max_length=120)
