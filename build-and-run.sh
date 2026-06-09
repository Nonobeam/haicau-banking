#!/bin/bash
set -e

echo "🔨 Starting clean build and services startup..."
echo "=================================================="

# Set Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "📦 Step 1: Maven clean build..."
mvn clean install -DskipTests -Dmaven.javadoc.skip=true -Dcheckstyle.skip -q
echo "✅ Maven build complete"

echo ""
echo "🐳 Step 2: Building Docker images (clean)..."
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml --profile apps build --no-cache
echo "✅ Docker images built"

echo ""
echo "🚀 Step 3: Starting all services..."
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml --profile apps up -d
echo "✅ Services started"

echo ""
echo "📊 Step 4: Checking service health..."
sleep 5
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml ps --format table

echo ""
echo "✨ All services are running!"
echo "=================================================="
echo ""
echo "📚 Service URLs:"
echo "  - General Ledger:      http://localhost:18081"
echo "  - Banking Reconcile:   http://localhost:18082"
echo "  - Platform Service:    http://localhost:8083"
echo "  - Kafka UI:            http://localhost:8090"
echo ""
echo "📖 View logs:"
echo "  docker compose logs -f [service-name]"
echo ""
