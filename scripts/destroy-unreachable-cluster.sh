#!/usr/bin/env bash
# Recovery-Destroy, wenn der EKS-Cluster bereits manuell (z.B. AWS-Konsole)
# gelöscht wurde und Pulumi an unerreichbaren Kubernetes-Resourcen scheitert.
#
# Schritte:
#   1) Alle kubernetes:*-Resourcen + den k8s-Provider aus dem workloads-State streichen
#   2) workloads-Stack destroyen
#   3) base-Stack destroyen (ECR, S3, EKS-Reste, VPC)
#
# Usage: ./scripts/destroy-unreachable-cluster.sh <dev|prod>
set -euo pipefail

ENV="${1:?usage: destroy-unreachable-cluster.sh <dev|prod>}"
case "$ENV" in dev|prod) ;; *) echo "ERROR: env must be dev or prod"; exit 1 ;; esac

command -v jq >/dev/null     || { echo "ERROR: jq fehlt"; exit 1; }
command -v pulumi >/dev/null || { echo "ERROR: pulumi fehlt"; exit 1; }

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORKLOADS_DIR="$ROOT_DIR/infra/workloads"
BASE_DIR="$ROOT_DIR/infra/base"

echo "=== Recovery-Destroy $ENV (Cluster ist bereits weg) ==="
read -p "Sicher? Räumt K8s-Resourcen aus dem Pulumi-State und destroyed dann beide Stacks. [yes/N] " -r CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "abgebrochen."
  exit 1
fi

# --- 1/3 K8s-Resourcen + Provider aus workloads-State entfernen ---
echo "--- 1/3 purge kubernetes:* aus workloads-State ---"
EXPORT_FILE="$(mktemp)"
trap 'rm -f "$EXPORT_FILE"' EXIT
pulumi -C "$WORKLOADS_DIR" -s "$ENV" stack export --file "$EXPORT_FILE"

TOTAL=$(jq -r '(.deployment.resources // []) | length' "$EXPORT_FILE")
echo "Resourcen im State: $TOTAL"

URNS=$(jq -r '
  (.deployment.resources // [])[]
  | select((.type // "") | startswith("kubernetes:") or . == "pulumi:providers:kubernetes")
  | .urn
' "$EXPORT_FILE")

if [ -z "$URNS" ]; then
  echo "keine kubernetes:*-Resourcen im State gefunden — überspringe."
else
  echo "$URNS" | while IFS= read -r urn; do
    [ -z "$urn" ] && continue
    echo "delete: $urn"
    pulumi -C "$WORKLOADS_DIR" -s "$ENV" state delete "$urn" --yes --target-dependents || true
  done
fi

# --- 2/3 Workloads-Stack destroyen ---
echo "--- 2/3 destroy workloads ---"
pulumi -C "$WORKLOADS_DIR" -s "$ENV" destroy --yes --continue-on-error

# --- 3/3 Base-Stack destroyen ---
echo "--- 3/3 destroy base ---"
pulumi -C "$BASE_DIR" -s "$ENV" destroy --yes --continue-on-error

cat <<EOF

=== Recovery-Destroy complete ===
Stacks bleiben als leere Pulumi-Stacks erhalten. Endgültig entfernen mit:
  pulumi -C infra/workloads stack rm $ENV --yes
  pulumi -C infra/base       stack rm $ENV --yes
EOF
