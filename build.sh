#!/bin/bash

# Build script for xmake-idea plugin

set -e

echo "Building xmake-idea plugin..."
echo "This will build CLion debug module first, then the main plugin."

# Build the entire project
./gradlew build

echo "Build completed successfully!"
echo "The plugin JAR is available in build/distributions/"
echo ""
echo "To run the plugin in development mode, use: ./run.sh"
