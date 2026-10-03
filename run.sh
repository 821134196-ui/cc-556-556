#!/usr/bin/env bash
# 一条命令启动：构建 Svelte 前端 → 打包 Quarkus → 启动（单进程同时提供 API 与页面）
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 1. 定位 Java 21 / Maven / Node ----------
if [ -z "${JAVA_HOME:-}" ]; then
  for d in "$HOME"/tools/jdk-21* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-21*; do
    [ -x "$d/bin/java" ] && export JAVA_HOME="$d" && break
  done
fi
if [ -z "${MAVEN_HOME:-}" ] && [ -d "$HOME/tools/apache-maven-3.9.9" ]; then
  export MAVEN_HOME="$HOME/tools/apache-maven-3.9.9"
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}${MAVEN_HOME:+$MAVEN_HOME/bin:}$PATH"

command -v java >/dev/null  || { echo "缺少 JDK 21，请安装后重试（或解压到 ~/tools/jdk-21*）"; exit 1; }
command -v mvn  >/dev/null  || { echo "缺少 Maven，请安装后重试（或解压到 ~/tools/apache-maven-*）"; exit 1; }
command -v node >/dev/null  || { echo "缺少 Node.js 18+（用于构建前端），请安装后重试"; exit 1; }

echo "==> Java: $(java -version 2>&1 | head -1)"
echo "==> Node: $(node -v)"

# ---------- 2. 构建前端到 src/main/resources/META-INF/resources ----------
echo "==> 构建 Svelte 前端"
cd frontend
[ -d node_modules ] || npm install
npm run build
cd ..

# ---------- 3. 打包并启动 Quarkus ----------
echo "==> 打包 Quarkus 应用"
mvn -B -q package -DskipTests

mkdir -p data
echo ""
echo "================================================================"
echo "  家政钟点工时长与结算系统已启动： http://localhost:8080"
echo "  演示三个场景可运行： ./demo.sh"
echo "  （SQLite 数据库文件： data/cleaning.db，重启自动重建并播种）"
echo "================================================================"
exec java -jar target/quarkus-app/quarkus-run.jar
