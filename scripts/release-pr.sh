#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

source ./scripts/release-lib.sh

base_branch="${RELEASE_BASE_BRANCH:-main}"
release_branch="release/next"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

head_subject="$(git log -1 --pretty=%s)"
if printf '%s\n' "$head_subject" | grep -Eq '^chore\(release\):'; then
  exit 0
fi

declare -a release_packages=()
declare -a changed_files=()
declare -a pr_body_lines=()

for pkg in "${RELEASE_PACKAGES[@]}"; do
  version_file="$(package_version_file "$pkg")"
  current_version="$(cat "$version_file")"
  latest_tag="$(latest_package_tag "$pkg")"
  ahead="$(commit_count_since_tag "$pkg" "$latest_tag")"

  if [[ "$ahead" == "0" ]]; then
    continue
  fi

  next_version="$(next_version_for_package "$pkg" "$current_version" "$latest_tag")"
  printf '%s\n' "$next_version" > "$version_file"

  generate_package_changelog "$pkg" "$next_version"
  notes_file="${tmp_dir}/${pkg}.md"
  generate_package_release_notes "$pkg" "$next_version" "$notes_file"

  release_packages+=("$pkg")
  changed_files+=("$version_file" "$(package_changelog_file "$pkg")")
  pr_body_lines+=("## ${pkg}")
  pr_body_lines+=("")
  pr_body_lines+=("This PR prepares \`${pkg}-${next_version}\`.")
  pr_body_lines+=("")
  pr_body_lines+=("- Commits since last tag: ${ahead}")
  pr_body_lines+=("- Source branch: \`${release_branch}\`")
  pr_body_lines+=("- Base branch: \`${base_branch}\`")
  pr_body_lines+=("")
  pr_body_lines+=("### Notes Preview")
  pr_body_lines+=("")
  while IFS= read -r line; do
    pr_body_lines+=("$line")
  done < "$notes_file"
  pr_body_lines+=("")
done

existing_pr_number="$(
  gh pr list \
    --base "$base_branch" \
    --head "$release_branch" \
    --state open \
    --json number \
    --jq '.[0].number // empty'
)"

if [[ "${#release_packages[@]}" -eq 0 ]]; then
  if [[ -n "$existing_pr_number" ]]; then
    gh pr close "$existing_pr_number" --delete-branch --comment "Closing stale automated release PR; no unreleased package changes remain on ${base_branch}."
  fi
  exit 0
fi

git checkout -B "$release_branch"
git config user.name 'github-actions[bot]'
git config user.email 'github-actions[bot]@users.noreply.github.com'
git add "${changed_files[@]}"

if ! git diff --cached --quiet; then
  if [[ "${#release_packages[@]}" -eq 1 ]]; then
    pkg="${release_packages[0]}"
    version="$(cat "$(package_version_file "$pkg")")"
    git commit -m "chore(release): ${pkg}-${version}"
  else
    git commit -m "chore(release): prepare package releases"
  fi
fi

git push --force-with-lease origin "$release_branch"

pr_body_file="${tmp_dir}/release-pr-body.md"
{
  echo "<!-- auto-release-pr -->"
  echo "## Release"
  echo
  echo "This PR prepares the next package release set."
  echo
  for pkg in "${release_packages[@]}"; do
    version="$(cat "$(package_version_file "$pkg")")"
    echo "- \`${pkg}-${version}\`"
  done
  echo
  printf '%s\n' "${pr_body_lines[@]}"
} > "$pr_body_file"

if [[ "${#release_packages[@]}" -eq 1 ]]; then
  pkg="${release_packages[0]}"
  version="$(cat "$(package_version_file "$pkg")")"
  pr_title="chore(release): ${pkg}-${version}"
else
  pr_title="chore(release): prepare package releases"
fi

if [[ -z "$existing_pr_number" ]]; then
  gh pr create \
    --base "$base_branch" \
    --head "$release_branch" \
    --title "$pr_title" \
    --body-file "$pr_body_file"
else
  gh pr edit "$existing_pr_number" \
    --title "$pr_title" \
    --body-file "$pr_body_file"
fi
