#!/bin/bash

# Build script for xmake-idea plugin

set -e

echo "Building and packaging xmake-idea plugin..."
echo "This will run tests and create the complete plugin distribution ZIP."

# Run the normal build and explicitly assemble the deployable plugin archive.
./gradlew build buildPlugin

echo "Build completed successfully!"
echo "The complete plugin ZIP is available at build/distributions/xmake-idea.zip"
echo ""
echo "To run the plugin in development mode, use: ./scripts/run.sh"
