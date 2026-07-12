FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# curl is used by the container healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Install deps first for layer caching.
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY . .

# Run as a non-root user in production. The media dir must exist before the
# chown so a named volume mounted there is writable by appuser. COPY can carry
# a read-only mode from the host (e.g. Windows/OneDrive checkouts strip the
# write bit), so restore owner-write explicitly or narration saves 500.
RUN mkdir -p media && chmod -R u+w media \
    && useradd --create-home --uid 10001 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8000/health || exit 1

# Dev default: migrate/seed then serve a single uvicorn worker. The production
# compose overrides this command to run gunicorn with multiple workers.
CMD ["sh", "-c", "python -m app.prestart && uvicorn app.main:app --host 0.0.0.0 --port 8000"]
