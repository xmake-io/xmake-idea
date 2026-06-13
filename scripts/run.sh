#!/bin/bash

# Run script for xmake-idea plugin in development mode

set -e

echo "Running xmake-idea plugin in development mode..."
echo "This will build the plugin and start IntelliJ IDEA with the plugin loaded."

# Run the plugin in development mode
./gradlew runIde

echo "Plugin development session ended."
echo ""
echo "To build the plugin without running, use: ./scripts/build.sh"
