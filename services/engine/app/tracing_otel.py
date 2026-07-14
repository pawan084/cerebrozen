"""OpenTelemetry wiring — exports OTLP traces (and optionally metrics) to the
AWS Distro for OpenTelemetry (ADOT) collector, which fans out to X-Ray (traces)
and Prometheus (metrics). This is the same pipe the Spring Boot services use.

Design notes:
  - OFF by default (config.OTEL_ENABLED). When off, every helper is a no-op and
    `get_tracer()` returns a no-op tracer, so there is zero overhead.
  - Optional deps: the `opentelemetry-*` packages are only needed when enabled. If
    they're absent we log a one-line hint and stay disabled (the app still boots).
  - Async export only: BatchSpanProcessor + PeriodicExportingMetricReader run on a
    background thread, so enabling this does NOT add user-facing turn latency.
  - X-Ray-compatible trace IDs: uses AwsXRayIdGenerator + AwsXRayPropagator so the
    collector's awsxray exporter produces valid X-Ray segments.
  - Idempotent: configure_tracing() is safe to call once at startup; repeat calls
    are ignored.
"""

from __future__ import annotations

import logging

from app import config

logger = logging.getLogger("cerebrozen.otel")

_CONFIGURED = False
_TRACER = None  # set to a real tracer once configured


def configure_tracing() -> bool:
    """Set up the global tracer/meter providers per config. Returns True if OTEL
    is now active. Best-effort: any failure degrades to disabled, never raises."""
    global _CONFIGURED, _TRACER
    if _CONFIGURED:
        return _TRACER is not None
    _CONFIGURED = True

    if not config.OTEL_ENABLED:
        logger.info(
            "otel.disabled",
            extra={"reason": "no OTLP endpoint or exporters are 'none'"},
        )
        return False

    try:
        from opentelemetry import trace
        from opentelemetry.sdk.extension.aws.trace import AwsXRayIdGenerator
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
        from opentelemetry.sdk.trace.sampling import ParentBased, TraceIdRatioBased

        OTLPSpanExporter = _span_exporter_cls()
    except Exception as exc:  # noqa: BLE001 — deps absent → stay disabled cleanly.
        logger.warning(
            "otel.deps_missing",
            extra={
                "error": str(exc),
                "hint": "pip install opentelemetry-sdk opentelemetry-exporter-otlp "
                "opentelemetry-sdk-extension-aws to enable OTEL",
            },
        )
        return False

    try:
        resource = Resource.create(
            {
                "service.name": config.OTEL_SERVICE_NAME,
                "deployment.environment": config.OTEL_ENV,
            }
        )
        provider = TracerProvider(
            resource=resource,
            id_generator=AwsXRayIdGenerator(),
            sampler=ParentBased(TraceIdRatioBased(config.OTEL_SAMPLE_RATIO)),
        )
        # Endpoint/protocol come from the standard OTEL_EXPORTER_OTLP_* env vars
        # (set via SSM), so the OTLP path is a config choice, not a code change.
        if config.OTEL_TRACES_ENABLED:
            provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
        trace.set_tracer_provider(provider)

        # Propagate in X-Ray header format so spans link across the existing services.
        try:
            from opentelemetry.propagate import set_global_textmap
            from opentelemetry.sdk.extension.aws.trace.propagation.aws_xray_propagator import (
                AwsXRayPropagator,
            )

            set_global_textmap(AwsXRayPropagator())
        except Exception as exc:  # noqa: BLE001 — propagator optional; traces still export.
            logger.warning("otel.propagator_skipped", extra={"error": str(exc)})

        _TRACER = trace.get_tracer("cerebrozen.langgraph")

        if config.OTEL_METRICS_ENABLED:
            _configure_metrics(resource)

        logger.info(
            "otel.enabled",
            extra={
                "endpoint": config.OTEL_EXPORTER_OTLP_ENDPOINT,
                "protocol": config.OTEL_EXPORTER_OTLP_PROTOCOL,
                "service": config.OTEL_SERVICE_NAME,
                "env": config.OTEL_ENV,
                "sample_ratio": config.OTEL_SAMPLE_RATIO,
                "traces": config.OTEL_TRACES_ENABLED,
                "metrics": config.OTEL_METRICS_ENABLED,
            },
        )
        return True
    except Exception as exc:  # noqa: BLE001 — never let observability break boot.
        logger.warning("otel.setup_failed", extra={"error": str(exc)})
        _TRACER = None
        return False


def _is_http_protocol() -> bool:
    """True when OTEL_EXPORTER_OTLP_PROTOCOL selects the HTTP exporter (port 4318)."""
    return config.OTEL_EXPORTER_OTLP_PROTOCOL in ("http", "http/protobuf", "httpprotobuf")


def _span_exporter_cls():
    """OTLP span exporter class for the configured protocol (gRPC default)."""
    if _is_http_protocol():
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    else:
        from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
    return OTLPSpanExporter


def _metric_exporter_cls():
    """OTLP metric exporter class for the configured protocol (gRPC default)."""
    if _is_http_protocol():
        from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
    else:
        from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
    return OTLPMetricExporter


def _configure_metrics(resource) -> None:
    """Set up the global meter provider (OTLP → collector → Prometheus). Best-effort.
    Endpoint/protocol come from the standard OTEL_EXPORTER_OTLP_* env vars."""
    try:
        from opentelemetry import metrics
        from opentelemetry.sdk.metrics import MeterProvider
        from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader

        reader = PeriodicExportingMetricReader(_metric_exporter_cls()())
        metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=[reader]))
        logger.info("otel.metrics_enabled")
    except Exception as exc:  # noqa: BLE001
        logger.warning("otel.metrics_skipped", extra={"error": str(exc)})


def get_tracer():
    """Return the configured tracer, or a no-op tracer when OTEL is off/unavailable."""
    if _TRACER is not None:
        return _TRACER
    try:
        from opentelemetry import trace

        return trace.get_tracer("cerebrozen.langgraph")  # no-op provider when unset
    except Exception:  # noqa: BLE001 — opentelemetry absent → tiny stub.
        return _NoopTracer()


class _NoopSpan:
    def set_attribute(self, *_a, **_k) -> None: ...
    def record_exception(self, *_a, **_k) -> None: ...
    def __enter__(self):
        return self

    def __exit__(self, *_a) -> bool:
        return False


class _NoopTracer:
    def start_as_current_span(self, *_a, **_k):
        return _NoopSpan()
