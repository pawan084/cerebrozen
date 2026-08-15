from pydantic import BaseModel, EmailStr, Field, field_validator


def _fits_bcrypt(v: str) -> str:
    """bcrypt 4.x RAISES above 72 bytes, so an over-long passphrase used to
    500 on signup/reset (register C28; `verify_password` catches the same
    error, which is how it stayed invisible). Refuse it as a 422 with words."""
    if len(v.encode()) > 72:
        raise ValueError("Passphrases longer than 72 bytes aren't supported — please use a shorter one.")
    return v


class SignupRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    name: str = Field(default="", max_length=120)

    @field_validator("password")
    @classmethod
    def _password_fits(cls, v: str) -> str:
        return _fits_bcrypt(v)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    #: Optional so a client can rotate using the httpOnly cookie instead
    #: (register E40). Native clients and the member web app keep sending it in
    #: the body; the admin console no longer holds a copy it *could* send.
    #: The route rejects a call that supplies neither.
    refresh_token: str | None = None


class TokenBody(BaseModel):
    """A link token (email verification)."""
    token: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class OtpRequestBody(BaseModel):
    email: EmailStr


class OtpVerifyBody(BaseModel):
    email: EmailStr
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")


class ResetPasswordRequest(BaseModel):
    token: str
    new_password: str = Field(min_length=8, max_length=128)

    @field_validator("new_password")
    @classmethod
    def _password_fits(cls, v: str) -> str:
        return _fits_bcrypt(v)


class AppleSignInRequest(BaseModel):
    """The identity token from Sign in with Apple, plus the display name Apple
    only provides on the very first authorization (optional thereafter)."""
    identity_token: str
    name: str = Field(default="", max_length=120)


class GoogleSignInRequest(BaseModel):
    """The ID token from Google Sign-In, plus the display name Google returns."""
    id_token: str
    name: str = Field(default="", max_length=120)
