"""
Azure AD JWT Token Validator.

Fetches JWKS keys from the Azure AD well-known endpoint, validates JWT
tokens (signature, audience, issuer, expiry), and extracts user claims.
Designed as a FastAPI dependency for route-level authentication.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import httpx
import structlog
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from jose.utils import base64url_decode
from pydantic import BaseModel

from config import AppConfig, get_config

logger = structlog.get_logger(__name__)

# ---------------------------------------------------------------------------
# FastAPI OAuth2 scheme – extracts the Bearer token from the Authorization
# header automatically.
# ---------------------------------------------------------------------------
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token", auto_error=False)


# ---------------------------------------------------------------------------
# Public data models
# ---------------------------------------------------------------------------
class UserClaims(BaseModel):
    """Validated user identity extracted from an Azure AD JWT."""

    oid: str
    name: str
    email: str
    roles: List[str] = []
    raw_claims: Dict[str, Any] = {}


# ---------------------------------------------------------------------------
# JWKS cache
# ---------------------------------------------------------------------------
@dataclass
class _JWKSCache:
    """In-memory cache for Azure AD JWKS keys with TTL."""

    keys: List[Dict[str, Any]] = field(default_factory=list)
    fetched_at: float = 0.0
    ttl_seconds: float = 3600.0  # 1 hour

    @property
    def is_expired(self) -> bool:
        return (time.time() - self.fetched_at) > self.ttl_seconds


_cache = _JWKSCache()


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------
async def _fetch_jwks(config: AppConfig) -> List[Dict[str, Any]]:
    """
    Fetch JSON Web Key Set from the Azure AD well-known endpoint.
    Results are cached for `_JWKSCache.ttl_seconds`.
    """
    global _cache

    if _cache.keys and not _cache.is_expired:
        return _cache.keys

    tenant_id = config.azure_tenant_id
    openid_config_url = (
        f"https://login.microsoftonline.com/{tenant_id}/v2.0/"
        ".well-known/openid-configuration"
    )

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            # Step 1: discover JWKS URI
            oidc_resp = await client.get(openid_config_url)
            oidc_resp.raise_for_status()
            jwks_uri = oidc_resp.json()["jwks_uri"]

            # Step 2: fetch actual keys
            jwks_resp = await client.get(jwks_uri)
            jwks_resp.raise_for_status()
            keys = jwks_resp.json().get("keys", [])

            _cache.keys = keys
            _cache.fetched_at = time.time()
            logger.info(
                "jwks_refreshed",
                key_count=len(keys),
                tenant_id=tenant_id,
            )
            return keys

        except httpx.HTTPError as exc:
            logger.error("jwks_fetch_failed", error=str(exc))
            # Return stale keys if available; better than hard-failing
            if _cache.keys:
                logger.warning("jwks_using_stale_cache")
                return _cache.keys
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Unable to fetch Azure AD signing keys",
            ) from exc


def _find_signing_key(
    keys: List[Dict[str, Any]], token_header: Dict[str, Any]
) -> Optional[Dict[str, Any]]:
    """Match the key used to sign the token by its Key ID (kid)."""
    kid = token_header.get("kid")
    if not kid:
        return None
    for key in keys:
        if key.get("kid") == kid:
            return key
    return None


def _build_rsa_public_key(jwk: Dict[str, Any]) -> Any:
    """
    Build an RSA public key object from a JWK dict.
    python-jose can accept the JWK dict directly for RS256 verification.
    """
    return jwk


# ---------------------------------------------------------------------------
# Token validation
# ---------------------------------------------------------------------------
async def validate_token(
    token: str, config: AppConfig
) -> UserClaims:
    """
    Validate an Azure AD JWT and return structured UserClaims.

    Checks:
    1. Signature against Azure AD JWKS
    2. Audience matches our client ID
    3. Issuer matches our tenant
    4. Token is not expired
    """
    try:
        # Decode header without verification to find the signing key
        unverified_header = jwt.get_unverified_header(token)
    except JWTError as exc:
        logger.warning("jwt_header_decode_failed", error=str(exc))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token header",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    # Fetch and find matching signing key
    keys = await _fetch_jwks(config)
    signing_key = _find_signing_key(keys, unverified_header)

    if signing_key is None:
        logger.warning(
            "jwt_signing_key_not_found",
            kid=unverified_header.get("kid"),
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token signing key not found",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Expected audience and issuer
    audience = config.azure_client_id
    issuer = (
        f"https://login.microsoftonline.com/"
        f"{config.azure_tenant_id}/v2.0"
    )

    try:
        payload: Dict[str, Any] = jwt.decode(
            token,
            signing_key,
            algorithms=["RS256"],
            audience=audience,
            issuer=issuer,
            options={
                "verify_aud": True,
                "verify_iss": True,
                "verify_exp": True,
                "verify_nbf": True,
                "verify_iat": True,
            },
        )
    except JWTError as exc:
        logger.warning("jwt_validation_failed", error=str(exc))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token validation failed: {exc}",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    # Extract user claims with safe defaults
    return UserClaims(
        oid=payload.get("oid", payload.get("sub", "")),
        name=payload.get("name", ""),
        email=payload.get(
            "preferred_username",
            payload.get("email", payload.get("upn", "")),
        ),
        roles=payload.get("roles", []),
        raw_claims=payload,
    )


# ---------------------------------------------------------------------------
# FastAPI dependency
# ---------------------------------------------------------------------------
async def get_current_user(
    token: Optional[str] = Depends(oauth2_scheme),
    config: AppConfig = Depends(get_config),
) -> UserClaims:
    """
    FastAPI dependency that validates the Bearer token and returns
    a UserClaims object.  Raises 401 if the token is missing or invalid.

    Usage:
        @app.get("/protected")
        async def protected(user: UserClaims = Depends(get_current_user)):
            ...
    """
    if token is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization token is missing",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return await validate_token(token, config)
