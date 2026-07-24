#!/usr/bin/env bash
#
# fetch-bootstrap.sh — stage an arm64-v8a bootstrap payload for a local build.
#
# Atomux does NOT commit bootstrap payloads (see AUDIT.md and the README in
# app/src/main/assets/itermux/bootstrap/arm64-v8a/). This script fetches a
# rootfs, repackages it as the exact xz-compressed tar the runtime expects, and
# drops it at:
#
#     app/src/main/assets/itermux/bootstrap/arm64-v8a/bootstrap.tar.xz
#
# The file is git-ignored, so it never gets committed. Re-run to refresh.
#
# ---------------------------------------------------------------------------
# Which rootfs?
# ---------------------------------------------------------------------------
# This is the MINIMUM-ESSENTIALS smoke-test payload. Its only job here is to
# prove the resolve -> detect -> extract -> READY install pipeline end to end
# on a real device/emulator.
#
#   * Default: Alpine arm64 minirootfs. Tiny (~3 MB), clean, well-documented.
#     Alpine is musl-based, which is fine for verifying extraction and a basic
#     `sh` session, but it is NOT a glibc rootfs.
#
#   * For real proot/proroot sessions you need a GLIBC arm64 rootfs (proroot's
#     guest expects glibc; see the jniLibs README). Point this script at a
#     Debian/Ubuntu arm64 rootfs tarball via ROOTFS_URL when you get there.
#
# Override the source without editing the script:
#
#     ROOTFS_URL="https://.../my-arm64-rootfs.tar.gz" scripts/fetch-bootstrap.sh
#     ROOTFS_LOCAL="/path/to/local-rootfs.tar.gz"      scripts/fetch-bootstrap.sh
#
# ROOTFS_URL / ROOTFS_LOCAL may be .tar.gz, .tgz, .tar, or .tar.xz — the script
# normalizes to .tar.xz.
# ---------------------------------------------------------------------------

set -euo pipefail

# --- config ---------------------------------------------------------------
ALPINE_VERSION="${ALPINE_VERSION:-3.20.10}"
ALPINE_BRANCH="${ALPINE_VERSION%.*}"   # e.g. 3.20.10 -> 3.20
DEFAULT_ROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_BRANCH}/releases/aarch64/alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"

ROOTFS_URL="${ROOTFS_URL:-}"
ROOTFS_LOCAL="${ROOTFS_LOCAL:-}"

# --- resolve repo paths ----------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEST_DIR="${REPO_ROOT}/app/src/main/assets/itermux/bootstrap/arm64-v8a"
DEST_FILE="${DEST_DIR}/bootstrap.tar.xz"

log() { printf '[fetch-bootstrap] %s\n' "$*" >&2; }
die() { printf '[fetch-bootstrap] ERROR: %s\n' "$*" >&2; exit 1; }

command -v xz  >/dev/null 2>&1 || die "xz is required (install xz-utils)."
command -v tar >/dev/null 2>&1 || die "tar is required."

mkdir -p "${DEST_DIR}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

# --- acquire the source archive -------------------------------------------
SRC_ARCHIVE="${WORK_DIR}/rootfs-src"
if [[ -n "${ROOTFS_LOCAL}" ]]; then
    [[ -f "${ROOTFS_LOCAL}" ]] || die "ROOTFS_LOCAL not found: ${ROOTFS_LOCAL}"
    log "Using local rootfs: ${ROOTFS_LOCAL}"
    cp "${ROOTFS_LOCAL}" "${SRC_ARCHIVE}"
    SRC_NAME="${ROOTFS_LOCAL}"
else
    URL="${ROOTFS_URL:-$DEFAULT_ROOTFS_URL}"
    SRC_NAME="${URL}"
    log "Downloading rootfs: ${URL}"
    if command -v curl >/dev/null 2>&1; then
        curl -fSL "${URL}" -o "${SRC_ARCHIVE}" || die "download failed: ${URL}"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "${SRC_ARCHIVE}" "${URL}" || die "download failed: ${URL}"
    else
        die "neither curl nor wget is available to download ${URL}"
    fi
fi

# --- normalize to a plain .tar, then recompress as .tar.xz -----------------
# Detect compression by magic bytes so extension mislabeling doesn't matter.
PLAIN_TAR="${WORK_DIR}/rootfs.tar"
MAGIC="$(head -c 6 "${SRC_ARCHIVE}" | od -An -tx1 | tr -d ' \n')"
case "${MAGIC}" in
    1f8b*)        log "Source is gzip; decompressing"; gzip -dc "${SRC_ARCHIVE}" > "${PLAIN_TAR}" ;;
    fd377a585a00) log "Source is xz; decompressing";   xz   -dc "${SRC_ARCHIVE}" > "${PLAIN_TAR}" ;;
    *)            log "Source assumed to be a plain tar"; cp "${SRC_ARCHIVE}" "${PLAIN_TAR}" ;;
esac

# Sanity: make sure it is actually a tar with a recognizable prefix layout.
if ! tar -tf "${PLAIN_TAR}" >/dev/null 2>&1; then
    die "source does not look like a valid tar archive: ${SRC_NAME}"
fi

log "Recompressing as xz -> ${DEST_FILE}"
xz -z -c -T0 "${PLAIN_TAR}" > "${DEST_FILE}"

SIZE="$(du -h "${DEST_FILE}" | cut -f1)"
log "Done. Staged bootstrap.tar.xz (${SIZE})."
log "Source: ${SRC_NAME}"
log ""
log "Reminder: this payload is git-ignored and will NOT be committed."
log "For real proot sessions, supply a glibc arm64 rootfs via ROOTFS_URL."
