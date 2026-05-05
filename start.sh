#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""
MINIO_STARTED_BY_US=0

cleanup() {
  echo ""
  echo "Stopping services..."
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
  if [ "$MINIO_STARTED_BY_US" = "1" ]; then
    docker rm -f productspec-minio-dev >/dev/null 2>&1 || true
  fi
  wait 2>/dev/null
  echo "Done."
}
trap cleanup EXIT INT TERM

echo "=== Checking MinIO ==="
if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
  echo "MinIO already running on localhost:9000 — using it."
else
  echo "Starting MinIO container (productspec-minio-dev)..."
  docker run -d --rm \
    --name productspec-minio-dev \
    -p 9000:9000 -p 9001:9001 \
    -e MINIO_ROOT_USER=minioadmin \
    -e MINIO_ROOT_PASSWORD=minioadmin \
    minio/minio:RELEASE.2025-09-07T16-13-09Z \
    server /data --console-address ":9001" >/dev/null
  MINIO_STARTED_BY_US=1

  echo "Waiting for MinIO to become ready..."
  for i in $(seq 1 30); do
    if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  echo "Creating bucket productspec-data..."
  docker run --rm --network host minio/mc:latest sh -c "
    mc alias set local http://localhost:9000 minioadmin minioadmin &&
    mc mb --ignore-existing local/productspec-data
  " >/dev/null
fi

export S3_ENDPOINT="http://localhost:9000"
export S3_BUCKET="productspec-data"
export S3_ACCESS_KEY="minioadmin"
export S3_SECRET_KEY="minioadmin"
export S3_PATH_STYLE="true"
export S3_REGION="us-east-1"

export AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-dev-secret-change-me-dev-secret-change-me-dev-secret}"
export AUTH_COOKIE_SECURE="${AUTH_COOKIE_SECURE:-false}"

echo "=== Starting Backend (Spring Boot) ==="
cd "$ROOT_DIR/backend"
./gradlew bootRun --quiet &
BACKEND_PID=$!

echo "=== Starting Frontend (Next.js) ==="
cd "$ROOT_DIR/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "MinIO:    http://localhost:9001 (console)"
echo ""
echo "Press Ctrl+C to stop services."

wait
