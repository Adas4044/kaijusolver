#!/bin/bash

# Quick status check for the optimizer

LOG_FILE="optimizer_aggressive.log"

echo "╔════════════════════════════════════════════════╗"
echo "║     Aggressive Optimizer - Status Check       ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# Check if running
if ps aux | grep "OptimalSolver" | grep -v grep > /dev/null; then
    PID=$(ps aux | grep "OptimalSolver" | grep -v grep | awk '{print $2}')
    echo "✅ Status: RUNNING (PID: $PID)"
else
    echo "⏸️  Status: STOPPED"
fi

echo ""
echo "🏆 BEST SCORE FOUND:"
BEST=$(grep "✨" "$LOG_FILE" 2>/dev/null | tail -1)
if [ -n "$BEST" ]; then
    echo "$BEST"
    SCORE=$(echo "$BEST" | awk -F'|' '{print $4}' | tr -d ' ')
    echo ""
    echo "   → Score: $SCORE"
else
    echo "   (no improvements yet)"
fi

echo ""
echo "📊 PROGRESS:"
RESTARTS=$(grep "AGGRESSIVE RESTART" "$LOG_FILE" 2>/dev/null | tail -1)
if [ -n "$RESTARTS" ]; then
    echo "$RESTARTS"
fi

echo ""
echo "📈 ALL IMPROVEMENTS:"
grep "✨" "$LOG_FILE" 2>/dev/null | tail -10 | while read line; do
    ITER=$(echo "$line" | awk '{print $1}')
    SCORE=$(echo "$line" | awk -F'|' '{print $4}' | tr -d ' ')
    TIME=$(echo "$line" | grep -o '([0-9]*s)' | tr -d '()')
    echo "  Iter $ITER: $SCORE ($TIME)"
done

echo ""
echo "To watch live: tail -f $LOG_FILE"
echo "To stop: pkill -f OptimalSolver"
