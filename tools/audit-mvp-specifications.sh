#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

files=()
while IFS= read -r file; do
  case "$file" in
    */uitgebreid.md|docs/adr/template.md) ;;
    docs/overzicht.md|docs/ketenscenarios.md|docs/processen/processen-en-entiteiten.md|docs/processen/software-factory-dispatcher.md|docs/platform/*.md|docs/stakeholder/*.md|docs/gedeelde-modules/*.md|docs/processen/productontwerp/api.md|docs/processen/productontwerp/mvp.md|docs/processen/productplanning/api.md|docs/processen/productplanning/mvp.md|docs/processen/kwaliteitsbewaking/api.md|docs/processen/kwaliteitsbewaking/mvp.md|docs/adr/*.md)
      files+=("$file")
      ;;
  esac
done < <(rg --files docs | sort)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "MVP-specificatieaudit vond geen bronbestanden." >&2
  exit 1
fi

for file in "${files[@]}"; do
  case "$file" in
    docs/platform/*) evidence="technical-foundation" ;;
    docs/stakeholder/*) evidence="product-stakeholder" ;;
    docs/gedeelde-modules/*) evidence="shared-capabilities" ;;
    docs/processen/productontwerp/*) evidence="product-design" ;;
    docs/processen/productplanning/*) evidence="product-planning" ;;
    docs/processen/kwaliteitsbewaking/*) evidence="quality" ;;
    docs/processen/software-factory-dispatcher.md) evidence="dispatcher" ;;
    docs/processen/processen-en-entiteiten.md|docs/overzicht.md|docs/ketenscenarios.md) evidence="end-to-end" ;;
    docs/adr/*) evidence="architecture-decisions" ;;
    *) echo "Geen bewijsrij voor $file" >&2; exit 1 ;;
  esac
  headings="$(rg -c '^#{1,6} ' "$file" || true)"
  norms="$(rg -ic '\b(moet|moeten|mag niet|alleen|altijd|precies|maximaal|invariant)\b' "$file" || true)"
  printf '%s\theadings=%s\tnorms=%s\tevidence=%s\n' "$file" "${headings:-0}" "${norms:-0}" "$evidence"
done

ruby - "${files[@]}" <<'RUBY'
files = ARGV
errors = []
files.each do |source|
  text = File.read(source)
  text.scan(/\[[^\]]+\]\(([^)]+)\)/).flatten.each do |target|
    path = target.split('#', 2).first
    next if path.empty? || path.match?(/\A(?:https?:|mailto:)/)
    resolved = File.expand_path(path, File.dirname(source))
    errors << "#{source}: ontbrekende relatieve link #{target}" unless File.exist?(resolved)
  end
end
abort(errors.join("\n")) unless errors.empty?
RUBY

if rg -n '\|[[:space:]]*`?(TODO|UNKNOWN)`?[[:space:]]*\|' docs/mvp-bewijsrecord.md >/dev/null; then
  echo "Het MVP-bewijsrecord bevat een open auditstatus." >&2
  exit 1
fi

printf 'MVP-specificatieaudit geslaagd voor %s bronbestanden.\n' "${#files[@]}"
