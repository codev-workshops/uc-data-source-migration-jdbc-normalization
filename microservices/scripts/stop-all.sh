#!/usr/bin/env bash
# =============================================================================
# Stop all microservices running locally
# =============================================================================

echo "Stopping all loan microservices..."

for port in 8080 8081 8082 8083; do
    pid=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -n "$pid" ]; then
        kill "$pid" 2>/dev/null && echo "  Stopped service on port $port (PID $pid)"
    else
        echo "  No service running on port $port"
    fi
done

echo "Done."
