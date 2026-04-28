#!/usr/bin/env bash
# One-time bootstrap for Pulumi state backend and stack initialization.
# Idempotent — safe to re-run.
set -euo pipefail

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION="eu-central-1"
STATE_BUCKET="productspec-pulumi-state-$ACCOUNT_ID"

echo "=== Bootstrap for AWS account $ACCOUNT_ID in $REGION ==="

# 1) State bucket
if aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null; then
  echo "State bucket already exists: $STATE_BUCKET"
else
  echo "Creating state bucket: $STATE_BUCKET"
  aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$REGION" \
    --create-bucket-configuration "LocationConstraint=$REGION"
  aws s3api put-bucket-versioning --bucket "$STATE_BUCKET" \
    --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption --bucket "$STATE_BUCKET" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-public-access-block --bucket "$STATE_BUCKET" \
    --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
fi

# 2) Pulumi login (always re-execute; cheap)
pulumi login "s3://$STATE_BUCKET?region=$REGION"

# 3) Stack init (idempotent)
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
for ENV in dev prod; do
  for PROJECT in base workloads; do
    DIR="$ROOT_DIR/infra/$PROJECT"
    if pulumi -C "$DIR" stack ls 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$ENV"; then
      echo "Stack $PROJECT/$ENV exists"
    else
      echo "Initializing stack $PROJECT/$ENV"
      pulumi -C "$DIR" stack init "$ENV"
    fi
  done
done

cat <<EOF

=== Bootstrap complete ===
State bucket:   s3://$STATE_BUCKET
Stacks created: productspec-base/{dev,prod}, productspec-workloads/{dev,prod}

Next steps:
  1. Set the OpenAI API key for each workloads stack:
     pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-...
     pulumi -C infra/workloads -s prod config set --secret openaiApiKey sk-...

  2. Deploy:
     ./scripts/deploy.sh dev
EOF
