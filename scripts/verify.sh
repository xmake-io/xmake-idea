#!/bin/bash

# XMake Plugin Verification Script
# This script runs the plugin verification task

set -e

echo "🔍 Verifying XMake Plugin..."
echo ""

# JAVA_HOME is set by CI (JetBrains JVM 21 via actions/setup-java)
# If running locally, ensure JAVA_HOME points to a JDK 21+ installation
if [ -z "$JAVA_HOME" ]; then
    echo "⚠️  JAVA_HOME not set, using system default (requires JDK 21+)"
fi

# Run the verification task
echo "🚀 Running plugin verification..."

# Check the result
if ./gradlew :verifyPlugin; then
    echo ""
    echo "✅ Plugin verification completed successfully!"
    echo ""
    echo "📋 Verification results are available in:"
    echo "   - build/reports/pluginVerifier/"
    echo ""
    echo "🔍 Check the reports for any compatibility issues or warnings."
else
    echo ""
    echo "❌ Plugin verification failed!"
    echo ""
    echo "🔍 Please check the error messages above for details."
    exit 1
fi
