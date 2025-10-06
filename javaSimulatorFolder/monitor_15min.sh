#!/bin/bash

# Monitor the 15-minute optimizer run
LOG_FILE="optimizer_15min.log"

while true; do
    clear
    echo "╔════════════════════════════════════════════════╗"
    echo "║  15-Min Optimizer with Diversity Monitor      ║"
    echo "╚════════════════════════════════════════════════╝"
    echo ""
    
    # Check if running
    if ps aux | grep "MultithreadedOptimalSolver" | grep -v grep > /dev/null; then
        PID=$(ps aux | grep "MultithreadedOptimalSolver" | grep -v grep | awk '{print $2}')
        echo "✅ Status: RUNNING (PID: $PID)"
    else
        echo "⏸️  Status: COMPLETED"
    fi
    
    echo ""
    echo "🏆 CURRENT BEST:"
    BEST=$(grep "✨" "$LOG_FILE" 2>/dev/null | tail -1)
    if [ -n "$BEST" ]; then
        echo "$BEST"
        SCORE=$(echo "$BEST" | awk -F'Score ' '{print $2}' | awk '{print $1}')
        echo ""
        echo "   → Score: $SCORE"
    fi
    
    echo ""
    echo "🔥 DIVERSITY REHEATS:"
    grep "DIVERSITY" "$LOG_FILE" 2>/dev/null | tail -5
    
    echo ""
    echo "📈 RECENT IMPROVEMENTS (last 10):"
    grep "✨" "$LOG_FILE" 2>/dev/null | tail -10 | while read line; do
        ITER=$(echo "$line" | awk '{print $4}')
        SCORE=$(echo "$line" | awk -F'Score ' '{print $2}' | awk '{print $1}')
        THREAD=$(echo "$line" | awk '{print $2}')
        echo "  Thread $THREAD iter $ITER: $SCORE"
    done
    
    echo ""
    echo "⏱️  Refreshing every 10 seconds... (Ctrl+C to stop)"
    
    sleep 10
done
