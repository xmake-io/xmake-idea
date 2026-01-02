#!/bin/bash

# XMake Plugin Verification Script
# This script runs the plugin verification task

set -e

echo "🔍 Verifying XMake Plugin..."
echo ""

# Set JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    # Try to find JDK
    if [ -d "$HOME/files/jdk-17.0.8.jdk/Contents/Home" ]; then
        export JAVA_HOME="$HOME/files/jdk-17.0.8.jdk/Contents/Home"
        echo "📦 Using JAVA_HOME: $JAVA_HOME"
    else
        echo "⚠️  JAVA_HOME not set, using system default"
    fi
fi

# Run the verification task
echo "🚀 Running plugin verification..."
./gradlew :verifyPlugin

# Check the result
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Plugin verification completed successfully!"
    echo ""
    echo "📋 Verification results are available in:"
    echo "   - build/reports/plugin-verifier/"
    echo ""
    echo "🔍 Check the reports for any compatibility issues or warnings."
else
    echo ""
    echo "❌ Plugin verification failed!"
    echo ""
    echo "🔍 Please check the error messages above for details."
    exit 1
fi
