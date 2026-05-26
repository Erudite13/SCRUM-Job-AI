"""
Centralized Application Configuration.

Uses pydantic-settings to load and validate all environment variables
at startup. A singleton pattern via get_config() ensures a single
config instance is reused across the application.
"""

from __future__ import annotations

import functools
from typing import List

from pydantic import Field, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class AppConfig(BaseSettings):
    """
    Application-wide configuration sourced from environment variables.
    All values are validated at startup; missing required fields will
    cause an immediate, descriptive failure.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── Azure OpenAI ────────────────────────────────────────────────
    azure_openai_endpoint: str = Field(
        ..., description="Azure OpenAI resource endpoint URL"
    )
    azure_openai_api_key: str = Field(
        ..., description="Azure OpenAI API key"
    )
    azure_openai_deployment: str = Field(
        default="gpt-4",
        description="Chat completion deployment name",
    )
    azure_openai_embedding_deployment: str = Field(
        default="text-embedding-ada-002",
        description="Embedding model deployment name",
    )
    azure_openai_api_version: str = Field(
        default="2024-02-01",
        description="Azure OpenAI API version",
    )

    # ── Azure Speech ────────────────────────────────────────────────
    azure_speech_key: str = Field(
        default="", description="Azure Cognitive Services Speech key"
    )
    azure_speech_region: str = Field(
        default="eastus", description="Azure Speech region"
    )

    # ── PostgreSQL / pgvector ──────────────────────────────────────
    db_host: str = Field(default="localhost", description="Database host")
    db_port: int = Field(default=5432, description="Database port")
    db_name: str = Field(
        default="scrummaster", description="Database name"
    )
    db_user: str = Field(default="postgres", description="Database user")
    db_password: str = Field(default="", description="Database password")
    db_pool_min_size: int = Field(
        default=2, description="Minimum connections in async pool"
    )
    db_pool_max_size: int = Field(
        default=10, description="Maximum connections in async pool"
    )

    @computed_field  # type: ignore[misc]
    @property
    def db_connection_string(self) -> str:
        """Standard PostgreSQL connection string."""
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    @computed_field  # type: ignore[misc]
    @property
    def db_async_connection_string(self) -> str:
        """Async-compatible PostgreSQL DSN for asyncpg."""
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    # ── Azure AD / Auth ────────────────────────────────────────────
    azure_tenant_id: str = Field(
        default="", description="Azure AD tenant ID"
    )
    azure_client_id: str = Field(
        default="", description="Azure AD application (client) ID"
    )
    azure_client_secret: str = Field(
        default="", description="Azure AD client secret"
    )

    # ── CORS ───────────────────────────────────────────────────────
    allowed_origins: str = Field(
        default="http://localhost:3000",
        description="Comma-separated list of allowed CORS origins",
    )

    @computed_field  # type: ignore[misc]
    @property
    def cors_origins(self) -> List[str]:
        """Parse comma-separated origins into a list."""
        return [
            origin.strip()
            for origin in self.allowed_origins.split(",")
            if origin.strip()
        ]

    # ── Downstream Services ────────────────────────────────────────
    sprint_service_url: str = Field(
        default="http://sprint-intelligence-service:8081",
        description="Sprint Intelligence .NET service base URL",
    )

    # ── Observability ──────────────────────────────────────────────
    log_level: str = Field(
        default="INFO", description="Root log level"
    )


@functools.lru_cache(maxsize=1)
def get_config() -> AppConfig:
    """
    Return the singleton AppConfig instance.

    The first call loads & validates env vars; subsequent calls
    return the cached object.
    """
    return AppConfig()  # type: ignore[call-arg]
