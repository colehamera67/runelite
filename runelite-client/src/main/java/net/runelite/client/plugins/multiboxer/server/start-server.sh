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

echo "Compiling server..."
javac MultiboxerServer.java
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to compile server"
    exit 1
fi
echo "Compilation successful!"
echo ""

echo "Starting server on port $PORT..."
echo ""

# Run the server (need to run from java root directory for classpath)
cd ../../../../../..
java -cp . net.runelite.client.plugins.multiboxer.server.MultiboxerServer $PORT
