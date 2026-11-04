#!/usr/bin/env bash
# OSS Exit Gate Release Script for D2: google/protobuf-gradle-plugin (protobuf-gradle/mavencentral)
# Authorized Builder SA: 847602531063-compute@developer.gserviceaccount.com
set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"
AR_URL="https://us-maven.pkg.dev/oss-exit-gate-prod/protobuf-gradle--mavencentral"
GCS_MANIFEST_DIR="gs://oss-exit-gate-prod-projects-bucket/protobuf-gradle/mavencentral/manifests"

ACCESS_TOKEN="$(gcloud auth print-access-token)"

echo "1. Staging com.google.protobuf:protobuf-gradle-plugin:${VERSION} to ${AR_URL}..."
./gradlew publishPluginMavenPublicationToExitGateRepository \
  -PossExitGateUrl="${AR_URL}" \
  -PossExitGateToken="${ACCESS_TOKEN}"

echo "2. Uploading OSS Exit Gate manifest to ${GCS_MANIFEST_DIR}..."
MANIFEST_FILE="$(mktemp)"
cat << EOF > "${MANIFEST_FILE}"
{
  "publish_all": false,
  "publishing_groups": [
    {
      "packages": [
        {
          "namespace": "com.google.protobuf",
          "name": "protobuf-gradle-plugin",
          "version": "${VERSION}"
        }
      ]
    }
  ]
}
EOF

gcloud storage cp "${MANIFEST_FILE}" \
  "${GCS_MANIFEST_DIR}/manifest-${VERSION}-$(date +%s).json"
rm -f "${MANIFEST_FILE}"
echo "Done! OSS Exit Gate pipeline triggered for com.google.protobuf:protobuf-gradle-plugin:${VERSION}."
