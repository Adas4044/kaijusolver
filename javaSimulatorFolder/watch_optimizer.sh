#!/bin/bash

# Watch the optimizer progress in real-time
# Usage: ./watch_optimizer.sh [log_file]

LOG_FILE="${1:-optimizer_100M_restart.log}"

echo "╔════════════════════════════════════════════════╗"
echo "║       Optimizer Progress Monitor               ║"
echo "╚════════════════════════════════════════════════╝"
echo ""
echo "Watching: $LOG_FILE"
echo "Press Ctrl+C to stop watching"
echo ""

while true; do
    clear
    echo "╔════════════════════════════════════════════════╗"
    echo "║       Optimizer Progress Monitor               ║"
    echo "╚════════════════════════════════════════════════╝"
    echo ""
    
    # Check if process is running
    if ps aux | grep "OptimalSolver" | grep -v grep > /dev/null; then
        echo "✅ Status: RUNNING"
    else
        echo "⏸️  Status: STOPPED"
    fi
    echo ""
    
    # Show all improvements
    echo "🏆 ALL IMPROVEMENTS FOUND:"
    echo "─────────────────────────────────────────────"
    grep "✨" "$LOG_FILE" 2>/dev/null | tail -15 | while read line; do
        # Extract score (4th column after |)
        score=$(echo "$line" | awk -F'|' '{print $4}' | tr -d ' ')
        iter=$(echo "$line" | awk -F'|' '{print $1}' | tr -d ' ')
        echo "  Iter $iter: Score $score"
    done
    
    echo ""
    echo "📊 CURRENT PROGRESS (last 5 lines):"
    echo "─────────────────────────────────────────────"
    tail -5 "$LOG_FILE" 2>/dev/null
    
    echo ""
    echo "🔄 Refreshing every 2 seconds... (Ctrl+C to stop)"
    
    sleep 2
done
