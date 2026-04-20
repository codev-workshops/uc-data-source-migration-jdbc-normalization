#!/usr/bin/env bash
# =============================================================================
# Start all microservices locally (without Docker)
# =============================================================================
# Usage: ./start-all.sh
#
# Starts each microservice as a background process.
# Use Ctrl+C or ./stop-all.sh to stop.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MS_DIR="$(dirname "$SCRIPT_DIR")"
PIDS=()

cleanup() {
    echo ""
    echo "Stopping all services..."
    for pid in "${PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo "  Stopped PID $pid"
        fi
    done
    exit 0
}

trap cleanup SIGINT SIGTERM

echo "=== Building all microservices ==="
cd "$MS_DIR"
mvn -q package -DskipTests

echo ""
echo "=== Starting microservices ==="

echo "Starting payment-service (port 8083)..."
java -jar "$MS_DIR/payment-service/target/payment-service-1.0.0.jar" &
PIDS+=($!)

echo "Starting borrower-service (port 8081)..."
java -jar "$MS_DIR/borrower-service/target/borrower-service-1.0.0.jar" &
PIDS+=($!)

sleep 5

echo "Starting loan-service (port 8082)..."
java -jar "$MS_DIR/loan-service/target/loan-service-ms-1.0.0.jar" &
PIDS+=($!)

sleep 5

echo "Starting api-gateway (port 8080)..."
java -jar "$MS_DIR/api-gateway/target/api-gateway-1.0.0.jar" &
PIDS+=($!)

echo ""
echo "=== All services started ==="
echo "  borrower-service: http://localhost:8081"
echo "  loan-service:     http://localhost:8082"
echo "  payment-service:  http://localhost:8083"
echo "  api-gateway:      http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop all services"

wait
