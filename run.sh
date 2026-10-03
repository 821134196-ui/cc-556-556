#!/usr/bin/env bash
# 一条命令启动：家政钟点工服务时长与迟到结算系统
# 自动准备 JDK17 / Maven（系统已装则直接用），构建前端，打包并运行 Quarkus。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$HOME/.housekeep-tools"
JDK_DIR_PATTERN="jdk-17*"
MVN_DIR_PATTERN="apache-maven-3.9.*"

log() { printf '\033[1;34m[run]\033[0m %s\n' "$*"; }

detect_java() {
  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE '"17\.|"21\.|"25\.'; then
    JAVA_HOME_DETECTED="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    return 0
  fi
  local d
  for d in "$TOOLS_DIR"/$JDK_DIR_PATTERN "$HOME/tools"/$JDK_DIR_PATTERN; do
    if [ -x "$d/bin/java" ]; then JAVA_HOME_DETECTED="$d"; return 0; fi
  done
  return 1
}

detect_mvn() {
  if command -v mvn >/dev/null 2>&1; then MVN_CMD="mvn"; return 0; fi
  local d
  for d in "$TOOLS_DIR"/$MVN_DIR_PATTERN "$HOME/tools"/$MVN_DIR_PATTERN; do
    if [ -x "$d/bin/mvn" ]; then MVN_CMD="$d/bin/mvn"; return 0; fi
  done
  return 1
}

install_tools() {
  mkdir -p "$TOOLS_DIR"
  case "$(uname -m)" in
    x86_64|amd64) ARCH_JDK=x64;  ARCH_MVN="" ;;
    aarch64|arm64) ARCH_JDK=aarch64; ARCH_MVN="" ;;
    *) echo "不支持的架构: $(uname -m)" >&2; exit 1 ;;
  esac

  if ! detect_java; then
    log "未找到 JDK 17，下载 Temurin 17 到 $TOOLS_DIR …"
    curl -fsSL -o "$TOOLS_DIR/jdk17.tar.gz" \
      "https://api.adoptium.net/v3/binary/latest/17/ga/linux/$ARCH_JDK/jdk/hotspot/normal/eclipse"
    tar -xzf "$TOOLS_DIR/jdk17.tar.gz" -C "$TOOLS_DIR"
    rm -f "$TOOLS_DIR/jdk17.tar.gz"
    detect_java
  fi

  if ! detect_mvn; then
    log "未找到 Maven，下载 Maven 3.9.9 到 $TOOLS_DIR …"
    curl -fsSL -o "$TOOLS_DIR/maven.tar.gz" \
      "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
    tar -xzf "$TOOLS_DIR/maven.tar.gz" -C "$TOOLS_DIR"
    rm -f "$TOOLS_DIR/maven.tar.gz"
    detect_mvn
  fi
}

install_tools
export JAVA_HOME="$JAVA_HOME_DETECTED"
export PATH="$JAVA_HOME/bin:$(dirname "$MVN_CMD"):$PATH"

cd "$ROOT_DIR"
mkdir -p data

# 1) 前端（Svelte + Vite），产物直接写入 Quarkus 静态资源目录
log "构建前端（Svelte）…"
(
  cd frontend
  [ -d node_modules ] || npm install
  npm run build
)

# 2) 后端打包（Quarkus 可执行 jar，内含 SQLite 驱动与前端静态资源）
log "构建后端（Quarkus）…"
"$MVN_CMD" -q package -DskipTests

JAR="target/quarkus-app/quarkus-run.jar"
log "启动服务： http://localhost:8080  （前端页面 + /api 接口）"
log "数据文件： $ROOT_DIR/data/housekeep.db （删除即可重置演示数据）"
exec java -jar "$JAR"
