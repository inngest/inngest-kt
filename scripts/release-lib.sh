#!/usr/bin/env bash

set -euo pipefail

readonly RELEASE_PACKAGES=("inngest" "inngest-spring-boot-adapter")

package_tag_pattern() {
  case "$1" in
    inngest)
      printf '%s\n' '^inngest-[0-9]+\.[0-9]+\.[0-9]+$'
      ;;
    inngest-spring-boot-adapter)
      printf '%s\n' '^inngest-spring-boot-adapter-[0-9]+\.[0-9]+\.[0-9]+$'
      ;;
    *)
      echo "unknown package: $1" >&2
      return 1
      ;;
  esac
}

package_include_path() {
  case "$1" in
    inngest)
      printf '%s\n' 'inngest/**'
      ;;
    inngest-spring-boot-adapter)
      printf '%s\n' 'inngest-spring-boot-adapter/**'
      ;;
    *)
      echo "unknown package: $1" >&2
      return 1
      ;;
  esac
}

package_version_file() {
  printf '%s/VERSION\n' "$1"
}

package_changelog_file() {
  printf '%s/CHANGELOG.md\n' "$1"
}

latest_package_tag() {
  local pkg="$1"
  git tag --list "${pkg}-*" --sort=-version:refname | head -n 1
}

commit_count_since_tag() {
  local pkg="$1"
  local latest_tag="$2"

  if [[ -n "$latest_tag" ]]; then
    git rev-list --count "${latest_tag}..HEAD" -- "$pkg"
  else
    git rev-list --count HEAD -- "$pkg"
  fi
}

next_version_for_package() {
  local pkg="$1"
  local current_version="$2"
  local latest_tag="$3"
  local log_output
  local range
  local major
  local minor
  local patch
  local bump="patch"

  if [[ -n "$latest_tag" ]]; then
    range="${latest_tag}..HEAD"
    log_output="$(git log --format='%s%n%b' "${range}" -- "$pkg")"
  else
    log_output="$(git log --format='%s%n%b' -- "$pkg")"
  fi

  if printf '%s\n' "$log_output" | grep -Eq 'BREAKING CHANGE|^[a-zA-Z0-9_-]+(\([^)]+\))?!:'; then
    bump="major"
  elif printf '%s\n' "$log_output" | grep -Eq '^feat(\([^)]+\))?:'; then
    bump="minor"
  fi

  IFS=. read -r major minor patch <<< "$current_version"

  case "$bump" in
    major)
      major=$((major + 1))
      minor=0
      patch=0
      ;;
    minor)
      minor=$((minor + 1))
      patch=0
      ;;
    patch)
      patch=$((patch + 1))
      ;;
    *)
      echo "unknown bump level: $bump" >&2
      return 1
      ;;
  esac

  printf '%s.%s.%s\n' "$major" "$minor" "$patch"
}

generate_package_changelog() {
  local pkg="$1"
  local next_version="$2"
  local changelog_file
  local tag_pattern
  local include_path

  changelog_file="$(package_changelog_file "$pkg")"
  tag_pattern="$(package_tag_pattern "$pkg")"
  include_path="$(package_include_path "$pkg")"

  git-cliff \
    --config cliff.toml \
    --tag "${pkg}-${next_version}" \
    --tag-pattern "$tag_pattern" \
    --include-path "$include_path" \
    --output "$changelog_file"
}

generate_package_release_notes() {
  local pkg="$1"
  local next_version="$2"
  local output_file="$3"
  local tag_pattern
  local include_path

  tag_pattern="$(package_tag_pattern "$pkg")"
  include_path="$(package_include_path "$pkg")"

  git-cliff \
    --config cliff.toml \
    --unreleased \
    --tag "${pkg}-${next_version}" \
    --tag-pattern "$tag_pattern" \
    --include-path "$include_path" \
    --strip header \
    --output "$output_file"
}

generate_latest_release_notes() {
  local pkg="$1"
  local output_file="$2"
  local tag_pattern
  local include_path

  tag_pattern="$(package_tag_pattern "$pkg")"
  include_path="$(package_include_path "$pkg")"

  git-cliff \
    --config cliff.toml \
    --latest \
    --strip header \
    --tag-pattern "$tag_pattern" \
    --include-path "$include_path" \
    --output "$output_file"
}

artifact_exists_in_maven_central() {
  local pkg="$1"
  local version="$2"
  local artifact_url
  local status

  artifact_url="https://repo1.maven.org/maven2/com/inngest/${pkg}/${version}/${pkg}-${version}.pom"
  status="$(curl -fsSIL -o /dev/null -w '%{http_code}' "$artifact_url" || true)"

  case "$status" in
    200)
      return 0
      ;;
    404)
      return 1
      ;;
    *)
      echo "unexpected Maven Central response while checking ${pkg}:${version}: ${status}" >&2
      return 2
      ;;
  esac
}

publish_package() {
  local pkg="$1"
  local version="$2"
  local auth
  local token

  (
    cd "$pkg"
    gradle publish --info
    ./maven-bundle

    auth="${MAVEN_USERNAME}:${MAVEN_PASSWORD}"
    token="$(printf '%s' "$auth" | base64)"

    curl -v -X POST "https://central.sonatype.com/api/v1/publisher/upload?name=com.inngest:${pkg}:${version}&publishingType=AUTOMATIC" \
      -H "Authorization: Bearer ${token}" \
      -F 'bundle=@bundle.zip'
  )
}
