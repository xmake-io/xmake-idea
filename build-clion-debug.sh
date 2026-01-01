#!/bin/bash

# Build script for CLion debug module

set -e

echo "Building CLion debug module..."

# Use main project's gradle to build the subproject
echo "Cleaning and building JAR..."
./gradlew :clion-debug:clean :clion-debug:jar -x :clion-debug:buildSearchableOptions

echo "Copying JAR to resources..."
./gradlew :clion-debug:copyToPluginResources

echo "CLion debug module built successfully!"
echo "JAR copied to main plugin resources directory."

echo "Build completed. The debug module JAR is now available in the main plugin."
