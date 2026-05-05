#!/usr/bin/env bash
# Tagge VPC-Subnets für ALB-Controller-Auto-Discovery.
# Public  -> kubernetes.io/role/elb=1            (für public-facing ALB)
# Private -> kubernetes.io/role/internal-elb=1   (für internal ALB)
#
# Usage: ./scripts/tag-subnets.sh <dev|prod>
set -euo pipefail

ENV="${1:?usage: tag-subnets.sh <dev|prod>}"
case "$ENV" in dev|prod) ;; *) echo "ERROR: env must be dev or prod"; exit 1 ;; esac

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

VPC=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output vpcId)
REGION=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output region 2>/dev/null || echo "eu-central-1")

echo "=== Tag subnets ($ENV) ==="
echo "VPC:    $VPC"
echo "Region: $REGION"

tag_subnets() {
  local filter="$1" role="$2" label="$3"
  local subnets
  subnets=$(aws ec2 describe-subnets \
    --filters "Name=vpc-id,Values=$VPC" "$filter" \
    --region "$REGION" \
    --query 'Subnets[].SubnetId' --output text)

  if [ -z "$subnets" ]; then
    echo "--- no $label subnets found ---"
    return
  fi

  echo "--- tag $label subnets ($role=1) ---"
  for s in $subnets; do
    echo "  $s"
    aws ec2 create-tags --resources "$s" \
      --tags "Key=$role,Value=1" \
      --region "$REGION"
  done
}

tag_subnets "Name=map-public-ip-on-launch,Values=true"  "kubernetes.io/role/elb"          "public"
tag_subnets "Name=map-public-ip-on-launch,Values=false" "kubernetes.io/role/internal-elb" "private"

echo ""
echo "=== verify ==="
aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$VPC" \
  --region "$REGION" \
  --query 'Subnets[].{id:SubnetId,az:AvailabilityZone,public:MapPublicIpOnLaunch,role:Tags[?starts_with(Key,`kubernetes.io/role`)].[Key,Value]}' \
  --output table

echo ""
echo "=== done ==="
echo "ALB-Controller anstoßen, damit Tags sofort gelesen werden:"
echo "  kubectl -n kube-system rollout restart deploy/aws-load-balancer-controller"
