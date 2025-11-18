#!/usr/bin/env bash
set -e

# Config via env vars
: "${GIT_REPO_SSH:?GIT_REPO_SSH env var is required, e.g. git@github.com:user/repo.git}"
: "${GIT_BRANCH:=main}"
: "${SSH_KEY_PATH:=/ssh/id_rsa}"
: "${MVN_EXTRA_OPTS:=}"
: "${RUN_JAR_PATTERN:=target/*.jar}"

echo "GIT_REPO_SSH=${GIT_REPO_SSH}"
echo "GIT_BRANCH=${GIT_BRANCH}"
echo "SSH_KEY_PATH=${SSH_KEY_PATH}"

# Prepare SSH
if [ ! -f "${SSH_KEY_PATH}" ]; then
  echo "SSH key not found at ${SSH_KEY_PATH}"
  exit 1
fi

# DEBUG / DIAGNOSTIC – SENSITIVE. Remove when done debugging.
echo "SSH key SHA256 hash: $(sha256sum "${SSH_KEY_PATH}" | awk '{print $1}')"

mkdir -p /root/.ssh
cp "${SSH_KEY_PATH}" /root/.ssh/id_rsa
chmod 600 /root/.ssh/id_rsa

# Set up known_hosts for GitHub (adjust if using a different host)
echo "Setting up known_hosts for github.com..."
ssh-keyscan -H github.com >> /root/.ssh/known_hosts 2>/dev/null || {
  echo "Warning: failed to fetch github.com host key with ssh-keyscan"
}
chmod 600 /root/.ssh/known_hosts

cd /opt/smarthouse

# Clone or update repo
if [ ! -d repo/.git ]; then
  echo "Cloning repository ${GIT_REPO_SSH} (branch: ${GIT_BRANCH}) from scratch..."
  rm -rf repo
  git clone --branch "${GIT_BRANCH}" "${GIT_REPO_SSH}" repo
  cd repo
else
  echo "Updating repository in /opt/smarthouse/repo..."
  cd repo

  echo "Before update, HEAD is: $(git rev-parse HEAD || echo 'unknown')"

  git fetch origin
  git checkout "${GIT_BRANCH}"
  git reset --hard "origin/${GIT_BRANCH}"
  git clean -fd

  echo "After update,  HEAD is: $(git rev-parse HEAD || echo 'unknown')"
fi

# Build application from current Git HEAD
echo "Building application with Maven wrapper..."
chmod +x ./mvnw
./mvnw clean package -DskipTests ${MVN_EXTRA_OPTS}

echo "Build finished. Looking for JAR with pattern: ${RUN_JAR_PATTERN}"
JAR_FILE=$(ls ${RUN_JAR_PATTERN} 2>/dev/null | head -n 1 || true)

if [ -z "${JAR_FILE}" ]; then
  echo "Error: no JAR found matching pattern '${RUN_JAR_PATTERN}'"
  exit 1
fi

echo "Ensuring external config directory exists at /var/smarthouse..."
mkdir -p /var/smarthouse

echo "Starting application from JAR: ${JAR_FILE}"
exec java \
  -Dspring.profiles.active=docker \
  -jar "${JAR_FILE}"
