#!/usr/bin/env bash
# Destroy productspec-agent stacks for the given environment.
# Usage: ./scripts/destroy.sh <dev|prod>
#
# Reihenfolge: workloads zuerst (Ingress, Deployments), dann base (Cluster, ECR, S3).
# Räumt zusätzlich ECR-Images und den S3-Datenbucket vor dem Destroy ab,
# damit Pulumi nicht an "RepositoryNotEmpty" / "BucketNotEmpty" hängenbleibt.
set -euo pipefail

ENV="${1:?usage: destroy.sh <dev|prod>}"
case "$ENV" in dev|prod) ;; *) echo "ERROR: env must be dev or prod"; exit 1 ;; esac

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Destroy $ENV ==="
read -p "Sicher? Das löscht den $ENV-Cluster, ECR-Images und Datenbucket. [yes/N] " -r CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "abgebrochen."
  exit 1
fi

REGION=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output region 2>/dev/null || echo "eu-central-1")

# --- 1/4 S3-Datenbucket leeren ---
echo "--- 1/4 empty S3 data bucket ---"
DATA_BUCKET=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output bucketName 2>/dev/null || true)
if [ -n "${DATA_BUCKET:-}" ]; then
  echo "leere s3://$DATA_BUCKET"
  aws s3 rm "s3://$DATA_BUCKET" --recursive --region "$REGION" || true
else
  echo "kein bucketName-Output gefunden — überspringe."
fi

# --- 2/4 ECR-Images löschen ---
echo "--- 2/4 purge ECR images ---"
for repo in $(aws ecr describe-repositories --region "$REGION" \
    --query "repositories[?contains(repositoryName,'productspec')].repositoryName" \
    --output text 2>/dev/null || true); do
  IMAGES=$(aws ecr list-images --repository-name "$repo" --region "$REGION" \
    --query 'imageIds[*]' --output json 2>/dev/null || echo '[]')
  if [ "$IMAGES" != "[]" ] && [ -n "$IMAGES" ]; then
    echo "leere $repo"
    aws ecr batch-delete-image --repository-name "$repo" --region "$REGION" \
      --image-ids "$IMAGES" >/dev/null || true
  fi
done

# --- 3/4 Workloads-Stack ---
echo "--- 3/4 destroy workloads ---"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" destroy --yes --continue-on-error

# --- 4/4 Base-Stack ---
echo "--- 4/4 destroy base ---"
pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" destroy --yes --continue-on-error

cat <<EOF

=== Destroy complete ===
Stacks bleiben als leere Pulumi-Stacks erhalten. Endgültig entfernen mit:
  pulumi -C infra/workloads stack rm $ENV --yes
  pulumi -C infra/base stack rm $ENV --yes
EOF
