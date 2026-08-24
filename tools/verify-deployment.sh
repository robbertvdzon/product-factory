#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPOSITORY_ROOT"

for overlay in acceptance production; do
  rendered="$(kustomize build "deploy/overlays/$overlay")"
  printf '%s' "$rendered" | kubectl create --dry-run=client --validate=false -f - >/dev/null
  if printf '%s' "$rendered" | rg -q '^kind: Secret$'; then
    echo "Error: plaintext Secret in $overlay-rendering." >&2
    exit 1
  fi
  if [[ "${PF_ALLOW_IMAGE_PLACEHOLDER:-false}" != "true" ]] && printf '%s' "$rendered" | rg -q 'manual-placeholder|:latest'; then
    echo "Error: niet-immutable image in $overlay-rendering." >&2
    exit 1
  fi
  printf '%s' "$rendered" | ruby -ryaml -e '
    YAML.load_stream(STDIN.read).compact.each do |resource|
      pod_spec = case resource["kind"]
                 when "Deployment" then resource.dig("spec", "template", "spec")
                 when "CronJob" then resource.dig("spec", "jobTemplate", "spec", "template", "spec")
                 end
      next unless pod_spec
      Array(pod_spec["containers"]).each do |container|
        security = container.fetch("securityContext")
        raise "rootcontainer: #{container["name"]}" unless security["runAsNonRoot"] == true
        raise "privilege escalation: #{container["name"]}" unless security["allowPrivilegeEscalation"] == false
        raise "capabilities: #{container["name"]}" unless Array(security.dig("capabilities", "drop")).include?("ALL")
        resources = container.fetch("resources")
        raise "resourcegrenzen: #{container["name"]}" unless resources["requests"] && resources["limits"]
      end
    end
  '
  echo "deployment-check: $overlay groen"
done

kubeseal --validate < deploy/overlays/production/sealed-secret-product-factory.yaml
