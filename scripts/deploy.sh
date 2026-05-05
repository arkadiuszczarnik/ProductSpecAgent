#!/usr/bin/env bash
# Deploy productspec-agent to AWS EKS for the given environment.
# Usage: ./scripts/deploy.sh <dev|prod>
set -euo pipefail

ENV="${1:?usage: deploy.sh <dev|prod>}"
case "$ENV" in dev|prod) ;; *) echo "ERROR: env must be dev or prod"; exit 1 ;; esac

DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY" != "0" ]; then
  echo "ERROR: uncommitted changes — commit first for reproducible image tags"
  git status --short
  exit 1
fi
GIT_SHA=$(git rev-parse --short=8 HEAD)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Deploy $ENV (git-sha: $GIT_SHA) ==="

# 1) Base stack
echo "--- 1/4 base stack ---"
pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" up --yes

# 2) ECR URLs
ECR_BACKEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrBackendUrl)
ECR_FRONTEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrFrontendUrl)
REGION=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output region)
ECR_REGISTRY="${ECR_BACKEND%/*}"

echo "--- 2/4 ECR login ---"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 3) Build + push (linux/amd64 für x86-Worker-Nodes t3a/t3.medium Spot)
echo "--- 3/4 build + push images ---"
docker buildx build --platform linux/amd64 \
  -t "$ECR_BACKEND:$GIT_SHA" --push "$ROOT_DIR/backend"
docker buildx build --platform linux/amd64 \
  -t "$ECR_FRONTEND:$GIT_SHA" --push "$ROOT_DIR/frontend"

# 4) Workloads stack
echo "--- 4/4 workloads stack ---"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" config set imageTag "$GIT_SHA"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" up --yes

INGRESS=$(pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" stack output ingressDnsName)
cat <<EOF

=== Deploy complete ===
Image tag: $GIT_SHA
Ingress:   http://$INGRESS

Note: ALB DNS may take 1-2 minutes to resolve after the first deploy.
EOF
