n-cube 'ToDo' list
======
### n-cube engine
* Datatypes
 * Add support for RULE-SET axis (RangeMap with Range and RangeSet).  Allows multi-match ranges with O(Log n) performance.
* Cell Prototype
 * For cube BAR in app FOO, the prototype could be specified as BAR.prototype - meaning that there is a prototype specific to the cube, -or-
   FOO.prototype meaning that this is the prototype for all expression cells in the app 'FOO' 
* MetaCommandCell
 * These are used for non-compiled commands.  First one needed, a 'cell pointer' that does not modify the input map in order to point.
 * Allow for 'impersonation' when pointing.
* Bring difficult computer science problems to non-programmers / business people:
 * AI / Optimizations / Solvers (Artificial Intelligence, Machine Learning, Predictive)
  * Add new train() and predict() APIs to make it dirt-simple for business folks to have access to a neural net
  * Genetic algorithm support for optimization and other such problems
  * Complete Constraint Satisfaction Problem (CSP) support
  * Natural language processing support
 * Infinite sized axis / n-cubes (process n-cube in a stream-like way where the entire n-cube is not in memory)
 * Lucene-like index capability for axis matching / indexing, as well as cube content indexing
 * Graph processing routines
 * Game theory
 * Decision theory
 * Simulated annealing
 * Swarm theory
 * Run all tests

### n-cube editor (NCE)
* TODO: create visualization of n-cube in NCE
* Run all tests
* Merge between non-HEAD branches
* Side-by-side comparison of two cubes using HtmlFormatter to generate display, add blank columns to ensure diff is same size
* Allow moving each delta from one n-cube to the other.
