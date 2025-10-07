# Kaiju Cats Optimization Strategy

## Performance Benchmarks

### Fast Simulator Performance
- **Single simulation**: ~25-50 μs (microseconds)
- **Throughput**: ~20,000-40,000 simulations/second (single-threaded)
- **With overhead** (loading commands): ~200-500 μs per iteration
- **Clean iterations** (SA optimizer): ~20,000 iterations/second

### Key Insight
The simulator is **FAST** enough for extensive search algorithms. We can easily run:
- 1 million simulations in ~50 seconds
- 10 million simulations in ~8-10 minutes

## Problem Characteristics

### Search Space
- **Valid tiles for commands**: Buildings (h, hh, H, HH) and Power Plants (P)
  - On test map: 17 valid tiles
  - Multi-floor buildings can hold 2 commands (floor 0 and floor 1)
  - Total positions: ~25-30 command slots

- **Command types**: 6 types (R, L, U, D, S, P) with costs $10-$30
- **Budget constraint**: $200 (can place ~6-20 commands depending on type)

- **Search space size**: Astronomical!
  - For each slot: 7 choices (6 commands + empty)
  - For 20 slots: 7^20 ≈ 8×10^16 possibilities
  - Budget constraint reduces this significantly but still huge

### Objective Function
1. **Primary**: Maximize total power score
2. **Heuristic** (your suggestion): Minimize cat distance to beds
   - Cats that reach beds get massive multipliers (3x or 5x)
   - Distance penalty helps guide search toward bed-reaching solutions

### Score Dynamics
- **Arrival bonuses**:
  - 1st cat to bed: +2000 power
  - 2nd cat to bed: 3x multiplier
  - 3rd cat to bed: 5x multiplier
- **Power sources**: Buildings destroyed, power plants (30 power), stomping
- **Power losses**: Spike traps (halve power), cat fights (winner-takes-all)

## Optimization Approaches

### 1. ✅ Simulated Annealing (Implemented)
**Status**: Working! Achieved 95,375 score in 1,000 iterations

**How it works**:
- Start with random solution
- Iteratively make small changes (add/remove/modify commands)
- Accept better solutions always
- Accept worse solutions probabilistically (decreasing over time)
- Temperature controls exploration vs exploitation

**Pros**:
- Escapes local optima (unlike greedy/hill climbing)
- Simple to implement and tune
- Works well with distance heuristic
- Fast iterations (~20k/sec)

**Cons**:
- Can get stuck in local optima if cooled too fast
- No guarantee of global optimum
- Hyperparameter sensitive (temperature, cooling rate)

**Tuning opportunities**:
- Cooling schedule (currently linear, try exponential or adaptive)
- Neighbor generation strategies (currently 3 strategies)
- Temperature restart (reheat when stuck)
- Multi-start (run multiple times with different seeds)

### 2. Genetic Algorithm
**How it would work**:
- Population of solutions
- Crossover: Combine two parent solutions
- Mutation: Random changes
- Selection: Keep best performers

**Pros**:
- Maintains diversity through population
- Good for complex landscapes
- Can parallelize easily

**Cons**:
- Slower per iteration (need to evaluate full population)
- Memory intensive
- Crossover design tricky for discrete commands

**When to use**: If SA plateaus or for long runs (millions of iterations)

### 3. Monte Carlo Tree Search (MCTS)
**How it would work**:
- Build tree of partial solutions
- Use UCB formula to balance exploration/exploitation
- Expand promising branches

**Pros**:
- Systematic exploration
- Good for sequential decision problems
- Proven in game AI

**Cons**:
- More complex to implement
- Memory intensive for large trees
- May not map well to our "all commands at once" problem

**When to use**: If we reformulate as sequential placement problem

### 4. Hybrid Approaches

#### 4a. Multi-Stage Optimization
1. **Stage 1**: Use greedy/heuristic to get cats close to beds
2. **Stage 2**: Use SA to maximize score given paths
3. **Stage 3**: Local refinement

#### 4b. Beam Search + SA
- Use beam search to find promising "regions"
- Run SA from multiple beam search results
- Keep best overall

#### 4c. Pattern Mining
- Run SA/GA to generate many good solutions
- Mine common patterns (e.g., "always place powerup at X,Y")
- Use patterns to seed new searches

### 5. Constraint-Based Approaches

#### Path Planning First
1. Use A* or Dijkstra to find optimal paths for each cat to their bed
2. Determine required turn commands to follow paths
3. Optimize power collection along determined paths
4. Use SA to fine-tune

**Pros**: Guarantees cats reach beds (if possible)
**Cons**: May miss creative solutions that don't follow shortest path

## Recommended Strategy

### Phase 1: Establish Baseline (Done!)
- ✅ Simulated Annealing working
- ✅ Achieved 95,375 score in 1,000 iterations (vs 140,375 current best)

### Phase 2: Tune Simulated Annealing
1. **Run longer**: 10,000-100,000 iterations
2. **Tune parameters**:
   - Try slower cooling (0.001 vs 0.003)
   - Try adaptive cooling based on improvement rate
   - Adjust distance penalty weight
3. **Better neighbor generation**:
   - Add "swap all commands on a tile" strategy
   - Add "clear and rebuild tile" strategy
   - Bias toward high-value positions (near cat starts/beds)

### Phase 3: Multi-Start SA
- Run 10-100 independent SA runs
- Keep best solution
- Analyze commonalities across top solutions

### Phase 4: Hybrid (if needed)
- Implement path planner to seed SA
- Or: Use SA result to seed genetic algorithm

### Phase 5: Problem-Specific Insights
- **Analyze current best solution** (140,375 score)
- What patterns make it work?
- Can we encode these as constraints or heuristics?

## Distance Heuristic Refinement

Your heuristic idea is excellent! Here are enhancements:

### Current Implementation
```
distancePenalty = manhattan_distance * 100 per unfinished cat
fitness = score - distancePenalty
```

### Possible Refinements

1. **Dynamic Penalty Weight**
   - Early in search: High weight (prioritize reaching beds)
   - Late in search: Lower weight (fine-tune score)

2. **Per-Cat Penalties**
   - Penalize cats not reaching beds more if they're close
   - Less penalty if cat has high power (defeated scenario might be OK)

3. **Path Feasibility**
   - Add penalty if path to bed is blocked by immovable obstacles
   - Reward if clear path exists

4. **Arrival Order Bonus**
   - Predict which cat reaches bed first
   - Bias toward having highest-power cat arrive 2nd or 3rd (for multiplier)

## Implementation Improvements

### A. Silent Simulator Mode
- Remove all print statements from GridMovementLoader for SA
- Speedup: 2-5x faster iterations

### B. Caching & Memoization
- Cache simulation results for identical command sets
- Hash command layout
- Likely not needed (simulations already very fast)

### C. Parallel SA
- Run multiple SA instances in parallel threads
- Each with different random seed
- Aggregate best results

### D. Incremental Evaluation
- When changing one command, don't re-simulate everything
- May be complex given game physics (butterfly effect)
- Probably not worth it

## Next Steps

1. **Immediate**: Run SA for 50,000-100,000 iterations and see results
2. **Compare**: vs current best solution (140,375)
3. **Analyze**: What patterns emerge in good solutions?
4. **Iterate**: Tune SA parameters based on results
5. **Scale**: If promising, run multi-start SA overnight

## Success Metrics

- **Target 1**: Match current best (140,375)
- **Target 2**: Beat current best by 10% (154,000+)
- **Target 3**: Find provably optimal solution (challenging!)

## Questions to Explore

1. Can all three cats reach their beds on this map?
2. What's the theoretical maximum score?
3. Are there multiple distinct solution strategies with similar scores?
4. How sensitive is the score to small command changes?

---

## Pseudocode for Enhanced SA

```java
// Multi-restart with adaptive cooling
for (int run = 0; run < numRuns; run++) {
    Solution current = generateInitialSolution();
    Solution best = current;

    double temp = initialTemp;
    int stagnantCount = 0;

    while (temp > minTemp) {
        for (int i = 0; i < itersPerTemp; i++) {
            Solution neighbor = generateNeighbor(current);

            if (accept(neighbor, current, temp)) {
                current = neighbor;

                if (current.score > best.score) {
                    best = current;
                    stagnantCount = 0;
                } else {
                    stagnantCount++;
                }
            }
        }

        // Adaptive cooling
        if (stagnantCount > 100) {
            temp *= 0.99;  // Cool faster when stuck
        } else {
            temp *= 0.995; // Cool slower when improving
        }

        // Reheat if very stuck
        if (stagnantCount > 500) {
            temp = initialTemp * 0.5;
            stagnantCount = 0;
        }
    }

    reportBest(run, best);
}
```

## Conclusion

The fast simulator opens up many optimization possibilities. **Simulated Annealing with your distance heuristic is a great starting point** and already shows promise. The key is to:

1. Run it longer (10k-100k iterations)
2. Tune it carefully (cooling, neighbor generation)
3. Use multi-start for robustness
4. Analyze patterns in good solutions

If SA plateaus, we can explore genetic algorithms or hybrid approaches. But start simple and iterate!
