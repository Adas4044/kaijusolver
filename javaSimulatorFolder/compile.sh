#!/bin/bash
# Compile all Java files into bin/ directory

# Create bin directory if it doesn't exist
mkdir -p bin

# Compile all Java files
echo "Compiling Java files..."
javac -d bin *.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful!"
    echo "✓ Class files are in bin/"
    echo ""
    echo "To run programs, use:"
    echo "  java -cp bin SAOptimizerV31 starting_simple.txt 60"
else
    echo "✗ Compilation failed"
    exit 1
fi
