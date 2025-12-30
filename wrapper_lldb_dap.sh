#!/bin/bash
# Wrapper to filter lldb-dap output and ensure clean DAP communication

# Log files for debugging
LOG_IN=/tmp/lldb-dap-in.log
LOG_OUT=/tmp/lldb-dap-out.log
LOG_ERR=/tmp/lldb-dap-err.log

# Ensure logs exist and are writable
touch $LOG_IN $LOG_OUT $LOG_ERR

# Header
echo "--- New Session $(date) ---" >> $LOG_IN
echo "--- New Session $(date) ---" >> $LOG_OUT
echo "--- New Session $(date) ---" >> $LOG_ERR

# Run lldb-dap
# 1. Tee stdin to LOG_IN, then pipe to lldb-dap
# 2. Redirect lldb-dap stderr to LOG_ERR (don't send to stdout!)
# 3. Tee lldb-dap stdout to LOG_OUT, then pipe to stdout (for CLion)

tee -a $LOG_IN | /usr/local/opt/llvm/bin/lldb-dap 2>> $LOG_ERR | tee -a $LOG_OUT
