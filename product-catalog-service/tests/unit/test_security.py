from datetime import timedelta
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.core.security import create_access_token, verify_jwt_token
from app.core.config import settings


def test_create_and_verify_valid_jwt_token():
    token = create_access_token({"sub": "admin@ecommerce.com", "role": "ROLE_ADMIN"})
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)

    payload = verify_jwt_token(credentials)
    assert payload["sub"] == "admin@ecommerce.com"
    assert payload["role"] == "ROLE_ADMIN"


def test_verify_expired_jwt_token_raises_http_401():
    token = create_access_token(
        {"sub": "user@ecommerce.com"},
        expires_delta=timedelta(seconds=-10)  # expired 10 seconds ago
    )
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)

    with pytest.raises(HTTPException) as exc_info:
        verify_jwt_token(credentials)

    assert exc_info.value.status_code == 401
    assert exc_info.value.detail == "Token has expired"


def test_verify_tampered_jwt_token_raises_http_401():
    token = create_access_token({"sub": "user@ecommerce.com"})
    # Tamper with signature bytes
    tampered_token = token[:-5] + "XXXXX"
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=tampered_token)

    with pytest.raises(HTTPException) as exc_info:
        verify_jwt_token(credentials)

    assert exc_info.value.status_code == 401
    assert exc_info.value.detail == "Invalid authentication token"
