#!/bin/bash
# RuneLite Multiboxer Server Startup Script (Linux/Mac)

echo "========================================"
echo " RuneLite Multiboxer Server"
echo "========================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 11 or higher"
    exit 1
fi

# Default port
PORT=43594

# Check for custom port argument
if [ ! -z "$1" ]; then
    PORT=$1
fi

echo "Starting server on port $PORT..."
echo ""

# Run the server
java -cp "../../../../../../../target/classes" net.runelite.client.plugins.multiboxer.server.MultiboxerServer $PORT
