#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

source ./scripts/release-lib.sh

merge_commit="${MERGE_COMMIT_SHA:-HEAD}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

git fetch --tags origin

declare -a release_packages=()

for pkg in "${RELEASE_PACKAGES[@]}"; do
  if git diff-tree --no-commit-id --name-only -r -m "$merge_commit" | grep -Fxq "$(package_version_file "$pkg")"; then
    release_packages+=("$pkg")
  fi
done

if [[ "${#release_packages[@]}" -eq 0 ]]; then
  echo "No package version files changed in merge commit ${merge_commit}" >&2
  exit 1
fi

git config user.name 'github-actions[bot]'
git config user.email 'github-actions[bot]@users.noreply.github.com'

for pkg in "${release_packages[@]}"; do
  version="$(cat "$(package_version_file "$pkg")")"
  tag_name="${pkg}-${version}"
  release_title="${pkg} ${version}"

  if git rev-parse "$tag_name" >/dev/null 2>&1; then
    echo "Tag ${tag_name} already exists; reusing it"
  else
    git tag "$tag_name" "$merge_commit"
    git push origin "$tag_name"
  fi

  if artifact_exists_in_maven_central "$pkg" "$version"; then
    echo "Skipping publish for ${pkg}:${version}; artifact already exists on Maven Central"
  else
    publish_package "$pkg" "$version"
  fi

  release_notes_file="${tmp_dir}/${pkg}-RELEASE_NOTES.md"
  generate_latest_release_notes "$pkg" "$release_notes_file"

  if gh release view "$tag_name" >/dev/null 2>&1; then
    gh release edit "$tag_name" \
      --title "$release_title" \
      --notes-file "$release_notes_file"
  else
    gh release create "$tag_name" \
      --target "$merge_commit" \
      --title "$release_title" \
      --notes-file "$release_notes_file" \
      --latest=false
  fi
done
