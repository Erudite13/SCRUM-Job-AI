"""
AI Scrum Master – FastAPI Application Entry Point.

Production-ready service providing:
- Sprint analysis via LangGraph multi-agent orchestrator
- Risk analysis endpoints
- DSM meeting initiation (called by n8n)
- Governance action classification
- Health / readiness probes
- Prometheus metrics exposure
- JWT authentication on all /api/ routes
- Structured logging via structlog
"""

from __future__ import annotations

import sys
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any, Dict, List, Optional

import httpx
import structlog
import uvicorn
from fastapi import (
    Depends,
    FastAPI,
    HTTPException,
    Request,
    Response,
    status,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_client import (
    CONTENT_TYPE_LATEST,
    Counter,
    Histogram,
    generate_latest,
)
from pydantic import BaseModel, Field

# ── Internal imports ────────────────────────────────────────────────
# Ensure the package root is on sys.path so sibling packages resolve.
import os

_PACKAGE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if _PACKAGE_ROOT not in sys.path:
    sys.path.insert(0, _PACKAGE_ROOT)

from auth.jwt_validator import UserClaims, get_current_user  # noqa: E402
from config import AppConfig, get_config  # noqa: E402

# ---------------------------------------------------------------------------
# Structured logging configuration
# ---------------------------------------------------------------------------
structlog.configure(
    processors=[
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.dev.ConsoleRenderer(),
    ],
    wrapper_class=structlog.make_filtering_bound_logger(
        structlog.get_config().get("min_level", 0)
    ),
    context_class=dict,
    logger_factory=structlog.PrintLoggerFactory(),
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger("fastapi_app")

# ---------------------------------------------------------------------------
# Prometheus metrics
# ---------------------------------------------------------------------------
REQUEST_COUNT = Counter(
    "http_requests_total",
    "Total HTTP requests",
    ["method", "endpoint", "status"],
)
REQUEST_LATENCY = Histogram(
    "http_request_duration_seconds",
    "HTTP request latency in seconds",
    ["method", "endpoint"],
)
LLM_CALL_COUNT = Counter(
    "llm_calls_total",
    "Total calls to LLM agents",
    ["agent"],
)

# ---------------------------------------------------------------------------
# Pydantic request / response models
# ---------------------------------------------------------------------------


class SprintInput(BaseModel):
    """Payload for sprint analysis."""

    sprint_id: str = Field(..., description="Unique sprint identifier")
    work_items: List[Dict[str, Any]] = Field(
        ..., description="List of work-item dicts from Azure DevOps"
    )
    include_rag_context: bool = Field(
        default=True,
        description="Whether to enrich analysis with RAG memory",
    )


class RiskInput(BaseModel):
    """Payload for standalone risk analysis."""

    sprint_id: str
    work_items: List[Dict[str, Any]]
    historical_velocity: Optional[float] = None


class MeetingInitiateInput(BaseModel):
    """Payload sent by n8n to start a DSM meeting."""

    meeting_id: str = Field(
        default_factory=lambda: str(uuid.uuid4()),
        description="Unique meeting identifier",
    )
    team_id: str
    participants: List[str] = []
    scheduled_time: Optional[str] = None


class ApprovalClassifyInput(BaseModel):
    """Payload for governance action risk classification."""

    action_description: str
    target_work_item: Optional[str] = None
    change_type: str = Field(
        default="update",
        description="Type of change: create, update, delete, reassign",
    )


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


class AnalysisResponse(BaseModel):
    sprint_id: str = Field(..., alias="sprintId")
    confidence_score: float = Field(..., alias="confidenceScore")
    risks: List[Dict[str, Any]]
    action_items: List[Any]
    rag_contexts_used: int = Field(
        0, alias="ragContextsUsed"
    )

    model_config = {"populate_by_name": True}


class RiskResponse(BaseModel):
    sprint_id: str = Field(..., alias="sprintId")
    risks: List[Dict[str, Any]]
    overall_risk_level: str = Field(..., alias="overallRiskLevel")


class MeetingResponse(BaseModel):
    meeting_id: str = Field(..., alias="meetingId")
    status: str
    websocket_url: str = Field(..., alias="websocketUrl")


class ApprovalClassifyResponse(BaseModel):
    risk_level: str = Field(..., alias="riskLevel")
    requires_human_approval: bool = Field(
        ..., alias="requiresHumanApproval"
    )
    reason: str


# ---------------------------------------------------------------------------
# Application lifespan – startup & shutdown hooks
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage application lifecycle resources."""
    config = get_config()
    logger.info(
        "app_starting",
        cors_origins=config.cors_origins,
        log_level=config.log_level,
    )

    # ── Startup: initialise RAG service connection pool ─────────
    try:
        from rag_memory_pipeline.rag_service import SprintRAGService

        rag_service = SprintRAGService(config)
        await rag_service.initialize()
        app.state.rag_service = rag_service
        logger.info("rag_service_initialized")
    except Exception as exc:
        logger.error("rag_service_init_failed", error=str(exc))
        app.state.rag_service = None

    yield

    # ── Shutdown: close pools ──────────────────────────────────
    if getattr(app.state, "rag_service", None) is not None:
        await app.state.rag_service.close()
        logger.info("rag_service_closed")

    logger.info("app_shutdown_complete")


# ---------------------------------------------------------------------------
# FastAPI application instance
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Autonomous AI Scrum Master Brain Engine",
    description=(
        "Cognitive Orchestration Service using LangGraph, Azure OpenAI, "
        "and pgvector RAG memory."
    ),
    version="2.0.0",
    lifespan=lifespan,
)


# ---------------------------------------------------------------------------
# Middleware
# ---------------------------------------------------------------------------
def _setup_cors(application: FastAPI, config: AppConfig) -> None:
    application.add_middleware(
        CORSMiddleware,
        allow_origins=config.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
    )


# Apply CORS at import time (config is validated here)
_setup_cors(app, get_config())


@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):
    """Log every request with timing and inject a correlation ID."""
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    structlog.contextvars.clear_contextvars()
    structlog.contextvars.bind_contextvars(request_id=request_id)

    start = time.perf_counter()
    response: Response = await call_next(request)
    elapsed = time.perf_counter() - start

    REQUEST_COUNT.labels(
        method=request.method,
        endpoint=request.url.path,
        status=response.status_code,
    ).inc()
    REQUEST_LATENCY.labels(
        method=request.method,
        endpoint=request.url.path,
    ).observe(elapsed)

    logger.info(
        "http_request",
        method=request.method,
        path=request.url.path,
        status=response.status_code,
        duration_ms=round(elapsed * 1000, 2),
    )
    response.headers["X-Request-ID"] = request_id
    return response


# ---------------------------------------------------------------------------
# Exception handlers
# ---------------------------------------------------------------------------
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    logger.warning(
        "http_error",
        status=exc.status_code,
        detail=exc.detail,
        path=request.url.path,
    )
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": True,
            "detail": exc.detail,
            "path": str(request.url.path),
        },
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception(
        "unhandled_error",
        path=request.url.path,
        error=str(exc),
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": True,
            "detail": "Internal server error",
            "path": str(request.url.path),
        },
    )


# ═══════════════════════════════════════════════════════════════════
#  ROUTES
# ═══════════════════════════════════════════════════════════════════


# ── Health & Metrics (unauthenticated) ─────────────────────────────
@app.get(
    "/healthz",
    response_model=HealthResponse,
    status_code=status.HTTP_200_OK,
    tags=["infrastructure"],
)
async def health_check():
    """Liveness probe for Kubernetes / Docker health checks."""
    return HealthResponse(
        status="healthy",
        service="ai-engine-orchestrator",
        version="2.0.0",
    )


@app.get("/metrics", tags=["infrastructure"])
async def prometheus_metrics():
    """Prometheus scrape endpoint."""
    return Response(
        content=generate_latest(),
        media_type=CONTENT_TYPE_LATEST,
    )


# ── Sprint Analysis ───────────────────────────────────────────────
@app.post(
    "/api/v1/ai/sprints/analyze",
    response_model=AnalysisResponse,
    tags=["sprint-intelligence"],
)
async def analyze_sprint(
    payload: SprintInput,
    request: Request,
    user: UserClaims = Depends(get_current_user),
):
    """
    Invoke the LangGraph multi-agent cognitive graph to analyse
    a sprint and produce action plans enriched with RAG context.
    """
    logger.info(
        "sprint_analysis_started",
        sprint_id=payload.sprint_id,
        user=user.email,
        work_item_count=len(payload.work_items),
    )
    LLM_CALL_COUNT.labels(agent="sprint_analysis").inc()

    # ── Optional RAG context enrichment ────────────────────────
    rag_contexts: List[Dict[str, Any]] = []
    if payload.include_rag_context and request.app.state.rag_service:
        try:
            query = f"Sprint {payload.sprint_id} analysis context"
            rag_contexts = await request.app.state.rag_service.retrieve_similar_contexts(
                query, limit=5
            )
            logger.info(
                "rag_contexts_retrieved",
                count=len(rag_contexts),
            )
        except Exception as exc:
            logger.warning("rag_retrieval_failed", error=str(exc))

    # ── Execute LangGraph agent orchestrator ──────────────────
    try:
        from langgraph_agents.agent_orchestrator import build_agent_graph

        agent_app = build_agent_graph()
        initial_state = {
            "sprint_id": payload.sprint_id,
            "work_items": payload.work_items,
            "rag_context": rag_contexts,
            "risks": [],
            "action_items": [],
            "governance_results": [],
            "token_usage": {},
        }

        import asyncio

        final_state = await asyncio.to_thread(
            agent_app.invoke, initial_state
        )

        # ── Store sprint memory for future RAG retrieval ──────
        if request.app.state.rag_service and final_state.get("action_items"):
            try:
                summary = (
                    f"Sprint {payload.sprint_id} analysis: "
                    f"{len(final_state['risks'])} risks, "
                    f"{len(final_state['action_items'])} actions"
                )
                await request.app.state.rag_service.store_sprint_memory(
                    payload.sprint_id, summary
                )
            except Exception as exc:
                logger.warning(
                    "rag_memory_store_failed", error=str(exc)
                )

        return AnalysisResponse(
            sprintId=final_state["sprint_id"],
            confidenceScore=final_state.get("confidence_score", 0.0),
            risks=final_state["risks"],
            actionItems=final_state["action_items"],
            ragContextsUsed=len(rag_contexts),
        )

    except Exception as exc:
        logger.exception(
            "sprint_analysis_failed",
            sprint_id=payload.sprint_id,
            error=str(exc),
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Sprint analysis failed: {exc}",
        ) from exc


# ── Standalone Risk Analysis ──────────────────────────────────────
@app.post(
    "/api/v1/ai/sprints/risks",
    response_model=RiskResponse,
    tags=["sprint-intelligence"],
)
async def analyze_risks(
    payload: RiskInput,
    user: UserClaims = Depends(get_current_user),
):
    """
    Run a focused risk analysis on sprint work items without
    the full multi-agent pipeline.
    """
    logger.info(
        "risk_analysis_started",
        sprint_id=payload.sprint_id,
        user=user.email,
    )
    LLM_CALL_COUNT.labels(agent="risk_analysis").inc()

    try:
        from langgraph_agents.agent_orchestrator import (
            AzureChatOpenAI,
            RiskAssessment,
        )
        from config import get_config as _cfg

        config = _cfg()
        model = AzureChatOpenAI(
            azure_deployment=config.azure_openai_deployment,
            azure_endpoint=config.azure_openai_endpoint,
            api_key=config.azure_openai_api_key,
            api_version=config.azure_openai_api_version,
            temperature=0.1,
        )

        context = "\n".join(
            [
                f"- {w.get('id')}: {w.get('title')} | "
                f"status={w.get('status')} assigned={w.get('assigned_to')} "
                f"effort={w.get('effort')} remaining={w.get('remaining')}"
                for w in payload.work_items
            ]
        )
        velocity_ctx = ""
        if payload.historical_velocity:
            velocity_ctx = (
                f"\nHistorical velocity: {payload.historical_velocity} SP/sprint."
            )

        prompt = (
            "You are a Senior Delivery Risk Analyst.\n"
            f"Evaluate these sprint work items for risks:{velocity_ctx}\n"
            f"{context}\n\n"
            "Return a JSON array of risk objects with fields: "
            "id, title, severity (LOW/MEDIUM/HIGH/CRITICAL), "
            "probability (0-1), impact, recommendation."
        )

        from langchain_core.messages import HumanMessage

        response = model.invoke([HumanMessage(content=prompt)])

        import json

        try:
            risks = json.loads(response.content)
            if not isinstance(risks, list):
                risks = [risks]
        except json.JSONDecodeError:
            risks = [{"analysis": response.content}]

        severity_order = {"CRITICAL": 4, "HIGH": 3, "MEDIUM": 2, "LOW": 1}
        max_severity = "LOW"
        for r in risks:
            sev = r.get("severity", "LOW")
            if severity_order.get(sev, 0) > severity_order.get(
                max_severity, 0
            ):
                max_severity = sev

        return RiskResponse(
            sprintId=payload.sprint_id,
            risks=risks,
            overallRiskLevel=max_severity,
        )

    except Exception as exc:
        logger.exception(
            "risk_analysis_failed", error=str(exc)
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Risk analysis failed: {exc}",
        ) from exc


# ── DSM Meeting Initiation (called by n8n) ────────────────────────
@app.post(
    "/api/v1/meetings/initiate",
    response_model=MeetingResponse,
    tags=["dsm-meetings"],
)
async def initiate_meeting(
    payload: MeetingInitiateInput,
    user: UserClaims = Depends(get_current_user),
):
    """
    Trigger a new Daily Scrum Meeting session. Returns a WebSocket
    URL that participants connect to for voice interaction.
    """
    logger.info(
        "meeting_initiated",
        meeting_id=payload.meeting_id,
        team_id=payload.team_id,
        user=user.email,
        participants=payload.participants,
    )

    config = get_config()
    websocket_url = f"ws://localhost:8001/ws/meeting/{payload.meeting_id}"

    # Notify sprint-intelligence-service about the meeting
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            await client.post(
                f"{config.sprint_service_url}/api/meetings/register",
                json={
                    "meetingId": payload.meeting_id,
                    "teamId": payload.team_id,
                    "participants": payload.participants,
                    "initiatedBy": user.email,
                },
            )
    except httpx.HTTPError as exc:
        logger.warning(
            "meeting_registration_failed",
            error=str(exc),
        )
        # Non-fatal – meeting can still proceed

    return MeetingResponse(
        meetingId=payload.meeting_id,
        status="initialized",
        websocketUrl=websocket_url,
    )


# ── Governance Action Classification ──────────────────────────────
@app.post(
    "/api/v1/ai/approvals/classify",
    response_model=ApprovalClassifyResponse,
    tags=["governance"],
)
async def classify_action(
    payload: ApprovalClassifyInput,
    user: UserClaims = Depends(get_current_user),
):
    """
    Classify an AI-proposed action's risk level for governance.
    LOW  → auto-approved
    MEDIUM/HIGH → requires human approval in the sprint-intelligence-service.
    """
    logger.info(
        "action_classification_started",
        description=payload.action_description[:100],
        user=user.email,
    )

    try:
        from langchain_openai import AzureChatOpenAI
        from langchain_core.messages import HumanMessage

        config = get_config()
        model = AzureChatOpenAI(
            azure_deployment=config.azure_openai_deployment,
            azure_endpoint=config.azure_openai_endpoint,
            api_key=config.azure_openai_api_key,
            api_version=config.azure_openai_api_version,
            temperature=0.0,
        )

        prompt = (
            "You are an AI Governance Analyst. Classify the risk level of "
            "the following proposed action.\n\n"
            f"Action: {payload.action_description}\n"
            f"Target Work Item: {payload.target_work_item or 'N/A'}\n"
            f"Change Type: {payload.change_type}\n\n"
            "Risk Levels:\n"
            "- LOW: cosmetic or informational changes (auto-approve)\n"
            "- MEDIUM: status updates, reassignments (needs review)\n"
            "- HIGH: deletions, sprint scope changes, critical path changes "
            "(needs human approval)\n\n"
            "Respond with ONLY a JSON object: "
            '{"risk_level": "LOW|MEDIUM|HIGH", "reason": "brief explanation"}'
        )

        response = model.invoke([HumanMessage(content=prompt)])

        import json

        try:
            result = json.loads(response.content)
        except json.JSONDecodeError:
            result = {"risk_level": "MEDIUM", "reason": response.content}

        risk_level = result.get("risk_level", "MEDIUM").upper()
        requires_approval = risk_level in ("MEDIUM", "HIGH")

        return ApprovalClassifyResponse(
            riskLevel=risk_level,
            requiresHumanApproval=requires_approval,
            reason=result.get("reason", "Classification completed"),
        )

    except Exception as exc:
        logger.exception(
            "action_classification_failed", error=str(exc)
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Action classification failed: {exc}",
        ) from exc


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    config = get_config()
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=False,
        log_level=config.log_level.lower(),
    )
