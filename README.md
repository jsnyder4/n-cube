n-cube
======
[![Build Status](https://travis-ci.org/jdereg/n-cube.svg?branch=master)](https://travis-ci.org/jdereg/n-cube)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/n-cube/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/n-cube)

n-cube is a Rules Engine, Decision Table, Decision Tree, Templating Engine, and Enterprise Spreadsheet, built as a hyper-space.  The Domain Specific Language (**DSL**) for rules is [**Groovy**](http://www.groovy-lang.org/). To include in your project:

```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>n-cube</artifactId>
  <version>3.4.87</version>
</dependency>
```
### Sponsors
[![Alt text](https://www.yourkit.com/images/yklogo.png "YourKit")](https://www.yourkit.com/.net/profiler/index.jsp)

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

<a href="https://www.jetbrains.com/idea/"><img alt="Intellij IDEA from JetBrains" src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" data-canonical-src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" width="100" height="100" /></a>
**Intellij IDEA**
___
####The image below is a Visual Summary of the main capabilities of n-cube.
![Alt text](https://raw.githubusercontent.com/jdereg/n-cube/master/n-cubeImage.png "n-cube Capabilities")

What are the components of an n-cube?
An n-cube has a set of axes (plural of axis), each of which adds a dimension. For example, in Excel, there are two axes, one axis numbered [rows] and one lettered [columns]. Within n-cube, each axis has a name, like 'State', 'Date', 'year', 'Gender', 'Month', etc.

Each axis contains columns.  In excel, the columns are just numbers and letters.  In n-cube, the columns can be a set of values like the months of a year, years, age ranges, price ranges, dates, states, coordinates (2D, 3D, lat/lon), expressions, and so on.

A column can be a simple data type like a String, Number, Date, but it can also represent a Range [low, hi) as well as a Set (a combination of discrete values and ranges).  A column can also contain expressions or any class that implements Java's Comparable interface.  It is these columns that input coordinates match (bind to).

Finally, there are cells.  In a spreadsheet, you have a row and column, like B25 to represent a cell.  In n-cube, a cell is similarly represented by a coordinate.  A Java (or Groovy) `Map` is used, where the key is the name of an axis, and the value is the value that will 'bind' or match a column on the axis.  If a value does not match any column on an axis, you have the option of adding a 'default' column to the axis.  Here's an example `[age:24, state:'CA', date:'2012/12/17']`.  The format given here for a `Map` is the declarative form used by Groovy.  In Java, that would be `map.put("age", 24)`, `map.put("state", "CA")` and so on.  Because the Groovy form is much shorter, it will be used from here on out to represent coordinate maps.  Because a `Map` is used as the input coordinate, you can have as many dimensions (keys) as desired.

Once an n-cube is set up, and a coordinate is also set up (e.g. Map coord = [age:24, state:'CA']), the most basic API to access it is `ncube.getCell(coord)`.  The return value will be the value of the cell at the given coordinate.  If the cell contains a simple value (`String`, `integer`, `Date`, `floating point number`, `boolean value`), it is returned.  If the cell contains an expression (written in Groovy), the expression is executed.  The return value for the cell in this case is the return value of the expression.

Expressions can be a simple as: `input.age > 17`, which would return `true` if the 'age' key on the input coordinate (map) was greater than 17, or `false` if not.  Expressions can be as complex as an entire `Class` with multiple methods (that can use other classes).  Expressions are written in Groovy.  See http://www.groovy-lang.org/.  Groovy was chosen because it is essentially Java (has Java syntax, compiles and runs at Java speed), but has many syntactic short-cuts that result in shorter code as compared to Java.

A cell in an n-cube can reference another cell within the same n-cube, like you might do in Excel.  For example, you may have a formula in Excel like this: `=b25 + b32 * 2`, stored say in `A1`.  The value for `A1` would be computed using the formula stored in `A1`.  N-cube allows these same capabilities, plus more (code / business logic).  A cell could have an `if` statement in it, a `for-loop`, `switch statement`, reference other cells within the same cube, or it can reference cells within different n-cubes.  The referenced cell can then be another formula, reference other cells, and so on.

### Rule Engine
When used as a rule engine, at least one axis within the n-cube is marked as as 'Rule' axis type.  In that case, each column is written as a condition (in Groovy).  For example, `input.age < 18`.  When a Rules n-cube is executed, each condition on the Rule axis is evaluated.  If the value is `true` (as how Groovy considers truth: http://www.groovy-lang.org/semantics.html#Groovy-Truth), then the associated cell is executed.  If no conditions are executed, and there is a default column on the rule axis, then the statement associated to the default column is executed.

To kick off the Rule execution, `ncube.getCell(coord, output)` is called. The conditions along the Rule axis are executed linearly, in order. Condition columns can reference values passed in on the input map (using `input.age`, `input.state`, etc.) as well as cells within other cubes.

The input coordinate map is referenced through the variable `input`.  The output map is referenced through the variable `output`.  Both can be referenced in the condition as well as in the cell (for expression, method, and template cells).  Typically, when used in rule mode, as conditions fire, the corresponding cell that is executed writes something to the output map.  For example in an pricing application, `state =='CA' || state == 'TX'` as the condition, the corresponding cell may have `output.productCost *= 1.07`.  The tax condition, for example.

The condition column can contain multiple statements.  Think of it like a method body.  The value of the last statement executed is evaluated as the condition. Your code has access to the input coordinate (map), output map, and the n-cube in which the code resides.  All Java code libraries and Groovy can be accessed as well.  For example, `println` from Groovy can be added to the conditions for debugging (as well as added to the executed cell).  The Groovy expression (or methods) in the executed cell can write multiple outputs to the output map.

As each condition on the Rule axis is executed, the n-cube rule engine writes information to a "_rule" entry into the output map.  This _rule entry is a `Map` which includes the condition name executed, the condition expression executed, and other useful information.  This allows you to evaluate the rule exection while developing the rules, to see rules fired.  This Map can be cast to `RuleInfo`, which has explicit APIs on it to retreive values from it, eliminating the need to know the keys.

In general, as cells execute, they write to the `output` map.  The `input` coordinate could be written to as well.  If it is modified, and a further n-cube is referenced, any modifications to the `input` coordinate will remain in place until that execution path returns.  When the execution path of the rules finishes and returns, the `input` map is restored to it's prior condition before execution. When returning then to an outer n-cube (or the code that called `ncube.getCell()`), that code will see no changes to the `input` map.  The `output` map will, of course, contain whatever changes were written to it.

Both condition columns and executed cells can tell the rule engine to restart execution of the conditions as well as to terminate any further conditions from being executed.  This is a linear rules execution flow, and intentionally not the RETE algorithm.

### Decision Table
When using n-cube as a decision table, each axis represents a decision variable.  For example, a state axis with all of the states of a country.  When accessed, the `input` coordinate would have the 'state' variable as a key in the input map, and the associated value to state would be a state, for example, 'KS' (Kansas).  If the data changes over time, it is common to add a 'date' axis.  Meaning that at one point in time, say for Kansas, the value 10 was returned, but within a different time frame, perhaps 11 is returned.

Common decision variables are country, state / providence, date, business unit, business codes, user role, actions, resources, and so no.  There is no limit to the number of axes that an n-cube can have (other than memory).

Decision tables are great and work best when all variable combinations make sense (a * b * c ... * n).  If the problem space has some combinations that do not make sense, then you may want to use n-cube's Decision Tree capability.  n-cube allows you to combine decision tables, decision trees, rules, and so on, ad infinitum.

### Decision Tree
A good example for a decision tree, is modeling the continents and countries of the world.  Not all continents have the same countries.  Therefore, it would not make sense to have an n-cube with one axis as 'continents' and another axis as 'countries.'  Instead, the initial (entry or outer n-cube) 'world' would have an axis 'continents', with columns Africa, Antarctica, Asia, Australia, Europe, North America, South America.  For each continent column, it's corresponding cell is a reference to a 'country' n-cube for that continent.  The cell reference is written like this: `@NorthAmericaCountries[:]` for example.  When this cell is executed, it in turn calls the 'NorthAmericaCountries' n-cube with the same input as was passed to the original ncube.  The `[ : ]` means that no modifications are being made to the input.  Additional inputs could be added here, as well as existing inputs could be changed before accessing the joined n-cube.

In the 'NorthAmericaCountries' n-cube, the cells would return a value (or if a subdivision of the countries is needed like 'States', the cells would join to yet further n-cubes modeling those subdivisions).  In order to 'talk to' or 'use' this n-cube decision tree, the code would look like this: `Map coord = [Continent:'NA', Country:'USA', State:'OH']` for example.  This would hit the North America column in the world n-cube, that cell would call the NorthAmericaCountries n-cube, which would then join to the 'UsaStates' n-cube.  To reach a Canadian province, for example, the input coordinate would look like this: `Map coord = [Continent:'NA', Country:'Canada', Province:'Quebec']`.  Notice that the 3rd parameter to the input is not state but province.  Both inputs work, because at each decision level, the appropriate n-cubes join to each other.

At each n-cube along the decision path, it could have additional 'scope' or dimensionality.  For example, a product axis may exist as a second axis on the cubes (or some of the cubes).  Think of a decision tree as stitching together multiple decision tables.  The cells are whatever you need them to be (Strings, numbers, Java objects, Groovy code to executed, etc.) In the case of code, think of your execution path of your program as going through a 'scope router' or 'scope filter' before the appropriate code is selected and executed.

### Template Engine
n-cube can be used to return templates (think of a template as an HTML page, for example, with replaceable parts - like mail merge.  Not limited to HTML, it could be any text file.)  When a template cell is executed, variables within the template are replaced (like mail merge).  If you have used the Apache project's Velocity project, Groovy templates, or have written JSP / ASP files, then you already have an idea on how to use templates.

Snippets written like this `<%  code or variable references   %>` or `${code / variable references}` can be added to the template.  Before the template is returned (think HTML page), these variable sections are executed.  The replaceable sections can reference n-cubes, for example, to get language specific content, region specific content, mobile / non-mobile content, browser specific content, and so on, to then fill-in a variable portion of the page.

Instead of actually storing the HTML, Groovy Code, etc. directly in an n-cube cell, the content can be referenced via a URL.  This allows the HTML page to be stored on a CDN (Content Delivery Network), and then selectively retrieved (per language, state, business unit, date, etc.) and then substitutions within the page made as well (if needed, using the templating mechanism).  Image files can be referenced this way as well, allowing different images to be retrieved depending on state, date, language, product, and so on.

### CDN Proxy Router
N-cube cells can be specified by URLs.  In the case of a Content Delivery Network, HTML files, Images, Javascript files, etc, can be also listed as URLs.  Used this way, the content is transferred back to the requesting (calling app).  Typically this is accomplished by using the UrlRewriteFilter (http://tuckey.org/urlrewrite/) inside Tomcat.  This filter is similar to the Apache webserver's mod_rewrite module.  By routing dyn/* to n-cube's CdnRouter class, the HTTP request will be proxied (resent) to the intended destination.  The HTTP response will then be returned to the original caller.

Used in this fashion, HTTP requests target a CDN n-cube, the n-cube may have axes on it for state, device type, date, etc., and depending on those may serve up different content depending on the logical name being requested.  For example, an HTML page uses a logical request like this: "dyn/html/account".  Notice that this is a logical URL.  No file extension is listed.  This request is received on Tomcat and redirected (using UrlRewriteFilter) to the n-cube CdnRouter.  The router makes a request to a 'decision tree' n-cube that first routes based on type (html, css, js, images, etc.).  This outer n-cube is a Decision tree that has a branch for each content type.

The next cube maps the logical name to the desired actual name.  In the example above, the HTML ncube has the logical HTML file names on one axis, and the cells have URLs to the real content.  This indirection allows the content to be moved without the page having to be changed.  Furthermore, if the page (or style sheet or Javascript code) returned needed to be different because of the user-agent device, the date, etc, then the routing cube can have an axis for each of these additional decision criteria.

HTTP Request ===> dyn/html/account ===> tomcat ===> UrlRewrite.xml ===> CdnRouter ===> content-n-cubes ===> physical file.  The content-n-cubes have the logical file names on the content.name axis, and the associated cell has the physical name.  If it is not found, the default cell will add the appropriate extension to the file type, and then make an attempt at fetching the content.  This way, these mime-type routing cubes only require entries on their axis when the logical to phsyical file name mapping is non-standard (changing based on device type, date, business unit, etc.)

### Creating n-cubes
Use either the Simple JSON format to create n-cubes, or the nCubeEditor to editing the pages.  At the moment, there is no cloud-based editor for n-cube, so you need to set up the nCubeEditor as a web-app within a Java container like tomcat or jetty.  See the sample .json files in the test / resources directories for examples.

These are read in using the NCubeManager.getNCubeFromResource() API.  You can also call ncube.fromSimpleJson(String json).

#### Licensing
Copyright 2012-2016 Cedar Software, LLC

Licensed under the Apache License, Version 2.0

___
### Version History
* 3.4.87
 * Minor tweak [removed one use of LOWER()]to main SELECT statement in the NCubePersister which fetches n-cubes matching various patterns.
* 3.4.86
 * Rename cube - now allows name to be only changed by case.
 * Bug fix: Meta-property values that have the URL or CACHE flag set to true now retain the setting.
* 3.4.85
 * The value-side of meta-properties on n-cube, axis, or column can now be any Java primitive type, String, Date, BigDecimal, BigInteger, Point2D, Point3D, LatLon, byte[], or any CommandCell     
* 3.4.84
 * A default cell value can be specified by a column meta-property ('default_value').  If columns on different axes specify a default value, then the value at the intersection is the same as the column default if the intersecting columns have the same value, otherwise the n-cube level default is returned.
 * The priority for cell value is 1) specified cell value, 2) column-level default, 3) n-cube default, 4) passed in default value to `at()`, `getCell()`.
* 3.4.83
 * `CdnClassLoader` updated to allow Groovy Grape annotations to fetch external content.
 * `CdnClassLoader` caches resource URLs (relative URL Strings that were expanded to fully qualified URLs against the classpath).  These are cleared when the class loader cache is cleared.
 * `CdnClassLoader` caches classes (dynamically loaded classes - using Groovy's @Grab as well as failed attempts [ClassNotFoundException]).  These are cleared when the class loader cache is cleared.
 * `CdnClassLoader` cache is per `ApplicationID`, therefore it's internal caches are non-static (NCube uses a classpath per ApplicationID - allowing for variations in the same class between versions - multi-tenant support).
* 3.4.82
 * Content search now supports wildcards.
 * Grape annotations extracted from inline Groovy to outside class definition.  This is to support Groovy's @Grab (dependency support). Additional changes forthcoming.
* 3.4.81
 * Fixed column ambiguously defined issue with updated search query.
* 3.4.80
 * Improved search query for SQL persister (uses left outer join instead of sub-select.  Sub-select was causing flip-flopping execution plan with Oracle query optimizer).
* 3.4.79
 * Converted remaining .java files to .groovy
* 3.4.78 
 * Transaction ID shared across persister APIs when necessary to bundle persister calls into same transaction.
 * Removed code related to generating multiple n-cube tests at once.
 * Converted NCubeTest code to Groovy
 * Removed StringValuePair class. NCubeTests now use CaseInsensitiveMap instead of Array of Map Entries.
* 3.4.76
 * Transaction ID added to all batch queries (Commit, Rollback, Delete, Restore, Update).  The ID is a unique ID that is added to the notes of each n-cube involved in the transaction.  This allows searching for all n-cubes with the same transaction ID.
* 3.4.75
 * Many performance optimizations
* 3.4.74
 * getCube(cubeName) API available to expression cells (inherited from NCubeGroovyExpression) now allows an optional 2nd argument whether the API should return null or throw an exception if the named cube does not exist.
 * Reduced the amount of memory used to store the Columns on an Axis.
* 3.4.73
 * 'at()' and 'go()' added to Regular expressions that scan for n-cube names
 * Used Groovy default arguments to eliminate multi-argument overloaded methods
 * Added API ncube.getNumPotentialCells() which returns the maximum number of cells in the hyperspace.
 * Reduced the definition of regular expressions to a shorter Groovy-style
 * Added many unit tests
* 3.4.72
 * Changed the internal storage of n-cube's cell keys to reduce their storage requirements.  Memory consumption by cell keys reduced by half.
 * Groovified NCube (from .java to .groovy) [@CompileStatic used to maintain full execution speed]
* 3.4.71
 * NCubeManager.copyBranch() added - copy from one ApplicationID to another ApplicationID (all cubes).  The target branch is always copied to in SNAPSHOT mode.  The target branch must be empty.
 * NCubeManager.getBranchCount() added.  This API will ask the persister directly how many n-cubes are in a particular branch.
 * CellInfo as Map - Converts a CellInfo into a Map instance
* 3.4.70
 * Performance enhancement: Permissions checks are faster
 * Persister logging showing method names and key arguments
* 3.4.69
 * Performance enhancement: Duplicate cube does not test for need of creating permission cubes if source and target ApplicationID are the same
 * Log persister APIs and times to correlate to REST calls (1st cut)
* 3.4.68
 * Performance enhancement: Only create cube and duplicate cube need to possibly create permissions cubes.  This check was being done on all updates, when it only should be done on create / duplicate.
* 3.4.67
 * Moved lock check code into separate call from assertPermissions.
* 3.4.66 
 * Performance enhancement: Ensure that sys.lock is never checked inside loop (lockBy value is obtained before any loops).
* 3.4.65
 * Simplified permissions checks
 * Changed NCubeManager cache to only cache NCube as opposed to NCube or NCubeInfoDto.
 * Changed NCubeManager cache - hid internals of .toLowerCase()
 * SQL quries always use LOWER() on n_cube_nm (used to only be Oracle)
 * Ensured that sys.lock is never cached
* 3.4.64
 * NCubeManager.releaseVersion(), check for SNAPSHOT versions removed (SNAPSHOT version will exist when this is called.)  Release check is still made.  
 * Delay removed.
* 3.4.63
 * tenant_cd compared with equals (=) and RPAD().  In a future release, the RPAD comparison will be dropped.
 * moveBranch() and releaseVersion() added to NCubeManager so that the release process can be done iteratively (looping through branches before calling releaseVersion() to prevent massive database update).
* 3.4.62
 * Fixed release cubes to NOT set the SHA1 to null on the HEAD branch
 * CREATE_DT needs to always be now.
* 3.4.59
 * Trim whitespace from userId in NCubeManager, because HTTP header source (or other sources) may surprise you with a trailing space, causing permissions lookups to fail.
* 3.4.49-58
 * Deubgging versions
* 3.4.48
 * Revert releaseCubes() / moveBranch() changes
* 3.4.47
 * Bug fix: Merging n-cubes with reference axes, need to handle both head to branch and branch to head.  More tests added.
* 3.4.46
 * Bug fix: Merging n-cubes with reference axes was not working correctly.  The HEAD cube's reference axis was not getting updated with the updated reference version.  Also, the 'sorted/not sorted' setting was not being auto-merged. 
* 3.4.45
 * Admin permissions are required to call clearCache().
* 3.4.44
 * Bug fix: Updating the version number of a transform cube was throwing an error.
 * Added check to Batch Update Reference Axis so that n-cubes that have been hand edited to an incorrect state, will be ignored (but logged).
* 3.4.43
 * Additional check added to NCubeManaer.releaseCubes() to ensure that the new version does not already exist.
 * NCubeJdbcPersister updated to use triple double quotes so that ${methodCall()} can be used instead of using string concatenation (+)
 * bug fix: duplicateCube() now auto-creates the persmissions cubes if the new cube causes a new app to be created.
* 3.4.42
 * During release of an Application, a sys.lock n-cube is add/updated to indicate that the release process is running and all mutable operations are blocked by it. 
* 3.4.41
 * bug fix: NCubeManager's constants were inadvertently made private when it was converted from Java to Groovy.  They are now public.
* 3.4.40
 * Added getMap() API that takes an output Map and default value.
* 3.4.39
 * Added branch permission support.  Users can elect to allow others read/update access to the n-cubes in their branch.
* 3.4.38
 * Added permissions checks to NCubeManager API.  If the `sys.usergroups` and `sys.permissions` n-cubes exist in an application, they will be consulted to determine whether or not the current user can execute the given API.  In addition, lists of cubes will be filtered based on these permissions.
* 3.4.37
 * Bug fix: Axis.updateColumns() should not allow duplicates - 2nd way found and fixed.
* 3.4.36
 * Bug fix: Axis.updateColumns() should not allow duplicates.
* 3.4.35
 * Bug fix: NCubeManager.updateReferenceAxes() must use RELEASE / HEAD as the status / branch. 
* 3.4.34
 * Added NCubeManager.updateReferenceAxes() which allows batch updating reference axes.
 * Extracted n-cube schema from JUnit test to it's own .sql file. 
* 3.4.33
 * Added NCubeManager.getReferenceAxes(appId), which returns all the NCube/Axis pairings as List<AxisRef>.  AxisRef contains the source (pointing) appId, cube name, and axis name, as well as the target app, version, cube name, and axis name, plus the optional transformation reference info.
* 3.4.32
 * New at() and go() APIs available on the NCubeGroovyExpression (cell).  These allow you to target n-cubes in other applications without referencing the NCubeManager.
 * Synchronization lifted for URL resource fetching (the serialization portion).  The resolution of the URL (relative to absolute) remains synchronized per URL String interned, e.g. parallel on different URLs, serial on a given URL.  
* 3.4.31
 * mergeAcceptMine() and mergeAcceptTheirs() can now process an array of cubes.  Makes it easy for front ends to allow user to merge a bunch of cubes at once.
 * Updated to use json-io 4.4.0.
* 3.4.30
 * Updated to pick up new version of java-util (1.20.5).
* 3.4.29
 * Input key tracking now available.  After a call to .getCell(), fetch the RuleInfo from the output Map and call ruleInfo.getInputKeysUsed() and it will return all the scope keys (Strings) that were referenced with either a .get() or a .containsKey().  This includes keys referenced from conditions on a Rule axis as well as meta-properties that are expressions.
* 3.4.28
 * Updated: removed NCubePersister.getAppVersions() - use getVersions()
 * bug fix: GroovyMethod cells were returning always as cacheable = true
* 3.4.27
 * Updated: NCubeManager.getAppVersions() only looks at app and tenant
 * Updated: NCubeManager.getVersions() only looks at app and tenant
* 3.4.26
 * Added NCubeManager.getBranches(appId), which looks at Tenant, App, Version, and Status to filter branch list.
 * Added NCubeManager.getVersions(String app) which returns a Map with two keys, 'SNAPSHOT' associated to List<String> of all SNAPSHOT version numbers, and 'RELEASE' associated to a List<String> of all RELEASE version numbers.
 * Changed NCubeManage.getAppNames(tenant) which returns all application names for the given tenant.
 * Future: NCubeManager.getAppVersions() will likely be dropped. 
* 3.4.25
 * bug fix: Axis.updateColumns() was dropping the Default column (not re-indexing it), causing cells associated to the default column to be orphaned.
* 3.4.24
 * When an n-cube is loaded, orphaned cells are now logged when found, but no longer throw an exception.  A cell can become orphaned if the Reference `Axis` it points to were to be modified.  Next version will not allow reference axis to non-RELEASE version and this becomes a mute point.
 * Enhancement: `ncube.breakAxisReference()`.  This API breaks the reference and effectively copies columns of the referenced axis to the referring axis.  
 * bug fix: The `CdnDefaultHandler` now ensures that it is not adding the same value more than once to the same axis.  Before, an `AxisOverlapException` was being thrown if more than one thread went after the same resource at the same time.  The blocked threads would still attempt to add the column to the axis.
 * bug fix: Situation where deadlock could occur between the `synchronized(this)` in `UrlCommandCell` and the `synchronized(GroovyBase.class)` in the `GroovyBase` compile.  The synchronization around compile is now tightened up against just the compile (parseClass) method, which does not re-enter any of n-cube code.  Further, the synchronization that was done at the execute() level in `UrlCommandCell` has been removed, and instead the synchronization is now around the URL resource resolution and resource fetching (using the String URL as the lock), the only places where dead-lock was occurring.
* 3.4.23
 * Enhancement: Reference Axis support added. Now, an axis can be defined once in a single cube (for example, all US states), and then that axis can be re-used on other n-cubes without redefining the axis (nor duplicating the storage).  Changing the original axis, will change all references.  Very use for 'availability' matrices (e.g. "which products are available by state"), managing 'exclusivity' between items ("which items work with each other"), etc. 
* 3.4.22
 * Enhancement: All axis types now load, delete, and update in `O(1)` or `O(Log n)`. They find within `O(1)` or `(O Log n)` excluding RULE cubes, where the conditions are linearly executed.  `Date` and `Number` axisValueTypes on `NEAREST` access in `O(Log n)`, the others are linear.
 * Enhancement: The n-cube merge process (`DeltaProcessor`) now handles more cases by locating columns by value, not ID (except with a RULE column that has no name).
 * For code executing in a 'exp' cell, use `at()` to pass on the current input and `go()` to start with a new map.  Both take a `Map`, but `at()` starts with the current input, whereas `go()` requires it to be fully built-up. `at()` is normally the API you want to fetch content from (at) another cell.
 * bug fix: When using a `GroovyTemplate`, if your code running within the template attempted to access a relative URL, it was not using the classpath from `sys.classpath` to anchor it.  Now, it uses the sys.classpath defined `CdnClassLoader` to anchor it.
 * `NCubeGroovyExpression` and `NCubeTemplateClosures.groovy` have two new APIs on them to allow quick and easy access to fetching content from a URL.  The `sys.classpath` entries will be used if these are relative.
 * Guava added as a dependency to the pom.xml file.
* 3.4.21
 * Renamed APIs added in 3.4.20 from 'at' to 'go'.
* 3.4.20
 * Within an n-cube `GroovyExpression` cell, at(Map coord, String cubeName [default this cube], Object defaultValue [default null]) can be used to reference another cell in same or another cube.  Use `at()` as `runRuleCube()`, `getRelativeCubeCell()`, and `getRelativeCell()` will be deprecated.
 * Within an n-cube `GroovyExpression` cell, atCoord(Map coord, String cubeName [default this cube], Object defaultValue [default null]) can be used to reference another cell in same or another cube.  Use `atCoord()` as `getFixedCell()` and `getFixedCubeCell()` will be deprecated.
* 3.4.19
 * Enhancement: `getCell()` now takes an optional parameter of defaultValue.  This is an additional way to provide a default value for when there is not cell at the passed in coordinate.
 * `getRequiredScope()` updated to take input and output Maps.  Because required scope can be an expression, this allows additional scope to be supplied to the expression.
 * `getOptionalScope()` updated to take input and output Maps.  Because optional scope can be an expression, this allows additional scope to be supplied to the expression.
* 3.4.18
 * `NCube.updateColumns()` now takes a String Axis Name as a `Collection` of Columns, rather than accepting an `Axis` which was not expected to be in proper order.  Simimlarly, the `Axis.updateColumns()` API now takes a `Collection` of `Columns`.
 * Continued work on Axis in support of a Reference Axis option.
* 3.4.17
 * Delta processing methods moved from `NCube` to `DeltaProcessor` class.  Use the static public methods on `DeltaProcessor` like this: `DeltaProcessor.getDelta(ncube1, ncube2)`, etc. 
* 3.4.16
 * Bug fix: `getTestDate()` failing.  Need to use JDBC-style API to access test_data_bin instead of pure Groovy approach of acecss (row.getBytes() not row['test_data_bin']) 
* 3.4.15
 * Enhancement: Cubes can now be merged from one branch to another (before merges were only between HEAD and branch).
 * Changed persister to use 'sub-select' statements instead of 'left outer joins' when locating highest revision number cube.  Seeing which of the two approaches is faster in a product environment.
* 3.4.14
 * Finalized test build-out in preparation for 3.5.0 release
 * Enhancement: Default columns now merge in (and out) along with cells associated to them.
* 3.4.13
 * The rest of the column merge tests added.
 * Added check to ensure that an axis with a duplicate ID cannot be added to the same n-cube.
 * Removed deprecated 4-argument `ApplicationID` constructor (it existed before there was branch support).
 * Bug fix: When merging rule axis and there was a column update (condition changed), it was merging it as a remove.
* 3.4.12
 * Bug fix: Merging columns between two cubes where they both added the same column value and had non-conflicting cell changes was getting reported as a merge conflict.  This is fixed.
 * More merge tests added, still more to come.
* 3.4.11
 * All SQL queries now include the originating method name in a SQL comment before the query text.  Helpful for performance monitoring.
 * `NCubeJdbcPersisterAdaptor` moved to Groovy and updated to use lambda function.  This removed all the boiler plate code and makes the Adaptor code much smaller.
 * Binding class updated to provide public API access to ALL of its fields including depth and value.
 * Added another test for merging columns.  Many more tests to come.
* 3.4.10
 * Increased robustness of colum merge.  Rule axes use rule name for locating rules and fall back to ID.
 * Uniqueness of rule names enforced - per axis.
* 3.4.9
 * Update / Commit branch - further increased instanes where automatic merge can be done (better rule axis support).
* 3.4.8
 * Update / Commit branch - increased instances where automatic merge can be done, including columns being added, deleted, or changed.  
 * Bug fix: When many threads accessed an expression cell at the same time, each thread was compiling the same code after it had its 'turn', rather than the first one getting it compiled, and then later threads picking up the compiled code.
 * Bug fix: When an expression cell is executed, the constructor and 'run()' method objects were being cached.  By changing the code not to cache these reflective objects, it eliminated an issue where a class cast exception was occurring on the same class (but loaded by different class loaders).  This was caused by a combination of different threads compiling a class, and the 'inflation-based' accessors that Java creates for reflective methods after 15 calls. 
* 3.4.7
 * Bug fix: `updateNotes()` and `updateTestData()` used SQL supported by HyperSonic and Oracle, but not MySQL.  Reverted to prior technique (2 queries - one to find max, one to update).
* 3.4.6
 * Added retry logic to URL resolution (tries twice - logs warning on each failed attempt).  If fails both times, an error is marked in the cell and it will not be tried again (Malformed URL, `sys.classpath` issue, etc).  
 * Added retry logic to URL content fetching (tries twice - logs warning on each failed attempt).  If it fails both times, future attempts at fetching will still be tried.
 * Added new JSON format that places the axes, columns, and cells in the JSON in a way that axes, columns, and cells are directly accessible as String keys in a `Map` - O(1).  Useful for sending to Javascript clients for quick access to the n-cube content.
 * Bug fix: Upating axis URL was not causing cube to be updated in database as SHA-1 was not being cleared.
 * Bug fix: `Column` order changes were not included in the SHA-1, causing no save to happen when only columns were re-ordered.
 * Bug fix: Parsing SET columns was failing in many cases.  JSON Format is used for `SET` columns as it is robust.  Requires `String` and `Date` variables to be quoted.  Instructions in NCE have been updated.
* 3.4.5
 * Bug fix on regex that parses import statements.  Import statements that were on the same line as code (code after the import statement) did not work.
* 3.4.4
 * `NCubeJdbcPersister` now supports batching for many APIs, including using batching via prefetch as well as SQL Update batching.  Completely re-written in Groovy to take advantage of Groovy's marvelous SQL support.  No checked exceptions reduces need for all the explicit SQL Exception handlers.  These are caught as runtime exceptions later.
 * `NCubeJdbcPersister` now works entirely with Lists of items instead of single items, where it makes sense.  This allowed batching support to be added for Delete, Restore, and Rollback.  Batching support added elsewhere in terms of SQL prefetch.
* 3.4.3
 * The `NCubeJdbcPersister` now allows for a `ConnectionProvider` that returns a null connection.  This can happen in a simple file-based persister, for example.
 * `NCubeManager.loadCube(NCubeInfoDto)` loaded the cube by id (parameter overkill).  The API is now `NCubeManager.loadCubeById(long id)`.  Useful for getting a specific revision of a cube.  User picks from revision list, code then loads the revision cube by ID.
* 3.4.2
 * Removed use of JDK 1.8 specific API to maintain 1.7 compatibility.  
 * Removed redundant API from NCubeManager (getCubeRecordsFromDatabase - use search() instead).
 * Fixed issue where cast of ClassLoad to GroovyClassLoader needed to be instanceof checked to prevent ClassCastException.
* 3.4.1
 * Improved exception handling of n-cube. When an exception is known (e.g. `CoordinateNotFound`) it is thrown as is. All unknown exceptions are caught, the n-cube call stack is added, and then it is rethrown in `CommandCellException`.  Call `.getCause()` on this exception to determine underlying exception.
 * All `CommandCell` related code and `CellInfo` converted from Java to Groovy.
 * Moved `recreate()` and `getType()` methods from `CellTypes` to `CellInfo`, and remove the CellTypes enumeration.
* 3.4.0
 * Added ability for n-cube Applications to define import list to make available to expression 'exp' cells / columns.  Add desired import classes to sys.prototype cube.  The cube has at least one axis (sys.property) and the column sys.imports.  The cell at this location returns a List of import classes or pacakges (do not include the 'import' keyword) and these packages will be added to the source of the compiled cell.
 * Added ability for n-cube Applications to define the class that expression cells inherit from (currently must be subclass of NCubeGroovyExpression).  Methods added to this class can be called in your source, allowing reduced source code size.  Add desired class name to sys.prototype cube.  On the sys.property axis, add a column sys.class and in the associated cell, place the String name of the class.  You can fully qualify the name, or use the shorter class name and add the package name to the import list (above bullet point).  NOTE: The class must be defined in the sys.classpath.
 * Added `NCubeManager.loadCube(appId, cubeName)` API which loads the cube from the persister (bypassing the cache).  It will cache the cube, but all calls to `loadCube()` will always use the persister to load.
 * Fixed issue where `NCubeManager.updateBranch()` was not skipped classes that you updated, but where not updated in the HEAD branch.  Nuisance issue, as it created additional revision of your modified cubes each time you ran update branch.
* 3.3.13
 * Update the 'UpdateBranch' code so that it handles the situation where the branch cube's SHA-1 matches the HEAD cube's SHA-1, yet the branch cube has a HEAD SHA-1 that no longer matches the HEAD.  This case can happen cubes are inserted into a branch directly in the database without going through the NCubeManager, leaving the headSha1 field null on that branch.  UpdateBranch detects this and Fast-Forwards the branch by simply updating the HEAD SHA-1 on the branch to be the same as the HEAD SHA-1.
 * Restore cube now adds the restored cube to the cache.  This may change in the future, as the n-cube-editor should not really be using the NCubeManager's cache, only a runtime n-cube instance should. 
* 3.3.12 
 * Removed looping-style APIs from persister (it should be focused on atomic changes) and moved that logic to the Manager so that all persisters leverage that code. These were the updateBranch (now updateCube), commitBranch (commitCube), rollbackCube (now rollbackCube).
 * Simplified coordinate names for columns that have a 'name' meta-property as they appear in a 'delta' difference description.
 * Made `NCube.getCoordinateFromColumnIds()` public.
 * Made some read-only getter APIs on `ApplicationID` public.
* 3.3.11
 * Bug fix: NPE was occurring when attempting to check two n-cubes for conflicts and the delta was null (non-comparable cubes).
* 3.3.10
 * Bug fix: Created, deleted and then restored cubes were incorrectly showing as conflicts, when there was no original head cube.
 * Bug fix: When choosing 'Accept Mine' in merge conflict resolution, the code was incorrectly pushing the branch cube to head.  Instead, it should have copied the head SHA1 to the branch so that it was updated for commit-overwrite on future commit.
 * Bug fix: NPE occurring when `Groovy Templates` (with empty content) were being scanned for cube-name references.  This happened during searching the cell content for cube references. Fixed.
 * Bug fix: NPE when computing SHA1 of column that had null value.  Granted, only the Default column should ever have a null, but to prevent NPE, null is converted to "" before .toString() for SHA1.
* 3.3.9
 * Bug fix: `HtmlFormatter` could throw an NPE when encoding the content of a cell.
* 3.3.8
 * Enhancement: Update Branch - no longer throws a BranchMergeException when there is a conflict.  Instead a Map is returned with keys of 'updates', 'merges', and 'conflicts'.  The 'updates' and 'merges' are now committed.  The conflicts are not committed and will therefore show up each time you run Update Branch until each one is resolved.
 * Enhancement: Rule condition parsing improved so that a condition on an 'expression' Axis can be passed in with the notation url|http://foo.bar.baxz.  The 'url' prefix is recognized and used to indicate that the subsequent text is a URL, not expression code.  Similarly, the prefix 'cache' can be used, which will indicate that the expression should be marked as cached.  Both 'url' and 'cache' can be combined --> url|cache|com/foo/bar/baz.groovy.  The order of 'url' or 'cache' prefix does not matter, and 0, 1, or both can be used. 
 * Bug fix: In the HTML view of an n-cube, rule condition formatting fixed when a rule condition was specified with a URL.  The anchor tag was including both the rule name and the URL (rule name should not have been included in the anchor tag).
 * The rule name in the HTML view is now highlighted in a such a way that the 'name:' text is no longer needed, making the condition look nicer as their is less text.  The rule name is set off from the page with a different background color.
* 3.3.7
 * `GroovyExpression` clears compiled class when cache=true (as it will only be used once).  Useful for cells containing expressions that returns Lists or Maps of data.
 * Added a small amount of padding to left and right side of column headers in the generated HTML.
 * Template API - increased APIs available to code running in `GroovyTemplate` replacement sections, e.g. `${ code }` and `<% code %>`.  APIs for `getCube()`, `getAxis()`, `getColumn()` as well as all Cedar Software APIs are imported so they do not need to be imported or qualified in your code. 
 * Bug fix: When an expression ended with '@foo[:]' and had another line of code below it, the preprocessor code dropped the newline causing this code not to compile.  The only work around was to add a semi-colon for the line.  This has been fixed.
 * Bug fix: `ContentCommandCell` now sets followRedirects flag (honors HTTP 301 / HTTP 302 redirect requests).
* 3.3.6
 * Restored Oracle specific code to allow for case-insensivity when comparing n-cube names.
* 3.3.5
 * Restore Oracle to it's default (case-sensitive) state.  This is temporary until the session-based case-insensitivity is introduced.
* 3.3.4
 * Fixed Oracle bug where name wasn't getting lowered correctly inside `NCubeJdbcPersister`
* 3.3.3
 * Default n-cube (table) level value - the default value can be a Groovy lookup expression, specified by URL or value, cache / not cached, etc.  The same value types as a cell are supported. 
 * Consolidated persister APIs to use search.
 * Updated Persister to be Oracle-aware so that n-cube names are handled case-insensitively.
* 3.3.2
 * Updated `NCubeManager.search()` API to be case-insensitive
* 3.3.1
 * Added API to allow retrieval of specific revision of an n-cube
* 3.3.0
 * Changed all `NCubeManager` APIs that returned `Object[]` containing `NCubeInfoDto`s to instead return `List<NCubeInfoDto>`.
 * Changed all persister APIs that returned `Object[]` containing `NCubeInfoDto`s to instead return `List<NCubeInfoDto>`.
 * Changed `NCubeManager.getAppNames()` and `NCubeManager.getAppVersions()` to return `List<String>` instead of `Object[]`.
 * These changes were necessary to prevent conversion from `List` to `Object[]` from occurring inside the database transaction, holding it open longer than necessary.
* 3.2.1
 * `NCubeReadOnlyPersister.loadCube()` API that took a long cube ID now takes an `NCubeInfoDto` instead, allowing the `NCubeJdbcPersister` to still use the n-cube ID, where as a test read only file persister may used fields from the `NCubeInfoDto` object instead.
* 3.2.0
 * This release adds complete support for branching.  All branch functionality is complete and unit tested, with > 98% code coverage.
 * Merge happens at cell - level - two people can work on the same cube without merge conflict if they change different cells.
 * The branch facility is implemented with the new branching APIs on `NCubeManager` (`createBranch`, `deleteBranch`, `updateBranch`,`merge`, etc.)
 * `NCUBE_PARAMS` is now read as either an environment variable or -D system property as a JSON map.  The keys in this map allow overriding the tenant, application, version, status, or branch.  Override support for other values may be added in the future.  This allows developer testing to switch Apps, verisons, etc., without having to change the `sys.bootstrap` cube.
 * All APIs that take a matching pattern on `NCubeManager` expect * or ? (match any, or match one).  These are converted internally within the respective persister to yield the expected behavior.
 * Performance: Support for memoization (caching) of `GroovyExpression` results has been added.  If the 'cache' value for an 'exp' (`GroovyExpression`) cell is true, the cell is executed, and the return value is cached (NOTE: The values in the input Map are NOT used as a key for the cached value).
 * Performance: Cubes are stored as compressed `byte[]` (gzip) of JSON when using the JDBC persister.
 * Performance: Cubes are serialized from Streams from the database, reducing the working set memory required when loading a cube.
 * Performance: Removed many instances of 'synchronized' (used for read only cache) and instead using `ConcurrentMap.putIfAbsent()` API.
 * Performance: Failed requests for an n-cube are cached - performance enhancement.
 * Performance: Two database queries reduced to into one query on straight up getCube() call when the cube was not in the cache and needed to be loaded.
 * Performance: Setting/Getting 133,000 cells reduced by a factor of 3 - takes about 1 second on Mac Book Pro 2.4Ghz.  Used to take 3.2 seconds.
 * Internal `Map` containing all cells is now `Map<Set<Long>, Object>` where the key is a set of column identifiers on each axis.  Before it was actual `Column` instances - less flexible.
 * New API: ncube.getPopulatedCellCoordinates().  This returns all cells that actually have values in them.  
* 3.1.7
 * Performance: `NCubeManager.getCube()` - caches failed fetches so that subsequent retries do not hit database again and again.
 * Performance: `NCube.getCell()` - when accessing a non-`CommandCell`, not need to set up the context (`Map` containing `NCube`, `Input`, and `Output`) 
* 3.1.4
 * Range and Set parsing for `Axis` values less picky and much more robust.  Useful when input for these columns are passed in from a GUI.
 * All unit tests are now completely written in Groovy.
 * Any .groovy file that is used internally for the implementation of the library now uses `@CompileStatic` for improved speed.
 * Further development on `GitPersister` (not complete, unaccessible).
* 3.1.3
 * Bug fix: Fixed bug in rule engine where `Boolean.equals()` was being called instead of `isTrue()` - which uses proper Groovy Truth.  This bug was introduced in 3.1.1.
* 3.1.2
 * Bug fix: Tightened up regex pattern match that is used to expand short-hand relative references into `getCell()` calls.  This prevents it from matching popular Java/Groovy annotations in the source code wtihin an expression cell.
 * Started work on `GitPersister`
* 3.1.1
 * Bindings to rule axis with a name is O(1) - directly starts evaluating the named condition.
 * Rule axis now has `fireAll` (versus fire once).  Fire all conditions is the default and what previously existed.  If the `fireAll` property of the `Axis` is set false on a Rule `Axis`, then the first condition that fires, will be the only condition that fires on that axis.
 * `NCubeInfoDto` now includes the SHA1 of the persisted n-cube.
 * bug fix: HTML and JSON formatters handle when the cell contents are a classLoader, as in the case of sys.classpath after it has been loaded.
 * bug fix: rule's with condition of null are now converted to false, allowing the JSON cube that contained such a condition to be loaded.
 * Rule Engine execution performance improvement - evaluation of the current axis to column binding set stops immediately if the rule axis is not bound (for the current condition being evaluated).
 * Many new tests added, including more concurrency tests
 * Moved to Log4J2
* 3.1.0
 * All JUnit test cases converted from Java to Groovy.
 * Improvement in classloader management.  Initially, a classloader per App (tenant, app, version) was maintained.  This has been further refined to support any additional scope that may have been added to the `sys.classpath` cube.  This allows a different URL set per AppID per additional scope like a business unit, for example.
* 3.0.10
 * Attempting to re-use `GroovyClassLoader` after `clearCache(appId)`. Discovered that the URLs do not clear.
* 3.0.9
 * Internal work on classpath management.  Fixing an issue where clearing the cache needed to reset the URLs within the `GroovyClassLoader`.
* 3.0.8
 * Bug fix: Threading issue in `NCubeManager` during initialization.  `GroovyClassLoaders` could be accessed before the resource URLs were added to the `GroovyClassLoader`.
 * Bug fix: `CdnClassLoader` was allowing .class files to be loaded remotely, which 1) is too slow to allow (.class files are attempted to be loaded with HTTP GET which fails very slowly with a 404, and 2) is insecure.  Instead, a future version will allow a 'white-less' of acceptable classes that can be remotely loaded.
* 3.0.6 / 3.0.7
 * Changed `getDeltaDescription()` to return a list of `Delta` objects, which contain the textual difference as well as the location (NCube, Axis, Column, Cell) of the difference and the type of difference (ADD, DELETE, UPDATE).
* 3.0.5
 * Added `getDeltaDescription()` to `NCube` which returns a `List` of differences between the two cubes, each entry is a unique difference.  An empty list means there are no differences.
* 3.0.4
 * Test results now confine all output to the RuleInfo (no more output in the output keys besides '_rule').
 * Formatting of test output now includes `System.out` and `System.err`.  `System.err` output shows in dark red (typical of modern IDEs).
* 3.0.3
 * Added `NCubeInfoDto` to list of classes that are available to Groovy Expression cells, without the author having to import it (inherited imports).
 * Added checks to NCubeManager to prevent any mutable operation on a release cube. Added here in addition to the perister implementations.
* 3.0.2
 * Improved support for reading cubes that were stored in json-io serialized format.
* 3.0.1
 * `NCubeManager` has new API, `resolveRelativeUrl()`.  This API will take a relative URL (com/foo/bar.groovy) and return an absolute URL using the sys.classpath for the given ApplicationID.
 * Bug fix: test data was being cleared when an update cube happened.  The test data was not being copied to the new revision.
* 3.0.0
 * `NCubeManager` no longer has `Connection` in any of it's APIs. Instead a `NCubePersister` is set into the `NCubeManager` at start up, and it uses the persister for interacting with the database.  Set a `ConnectionProvider` inside the `Persister` so that it can obtain connections.  This is in preparation of MongoDB persister support.
 * Cubes can now be written to, in addition to being read from while executing.  This means that n-cube can now be used for transactional data.
 * Caching has been greatly improved and simplified from the user's perspective.  In the past, `NCubeManager` had to be told when to load cubes, and when it could be asked to fetch from the cache.  With the new caching strategy, cubes are loaded with a simple `NCubeManager.getCube()` call.  The Manager will fetch it from the persister if it is not already in it's internal cache.
 * Revision history support added. When a cube is deleted, updated, or restored, an new record is created with a higher revision number.  Cubes are never deleted.  This enabled the new restore capability as well as version history.
 * `NCubeManager` manages the classpath for each Application (tenant, app, version, status).  The classpath is maintained in the `sys.classpath` cube as `List` of `String` paths (relative [resource] entries as well as jar entries supported).
 * `NCubeManager` manages the Application version.  `NCubeManager` will look to the 0.0.0 SNAPSHOT version of the `sys.bootstrap` cube for the App's version and SNAPSHOT. This makes it simple to manage version and status within this single cube.
 * When loading a `GroovyExpression` cell that is specified by a relative URL that ends with .groovy, and attempt will be made to locate this class already in the JVM, as might be there when running with a code coverage tool like Clover.
 * `ApplicationID` class is available to `GroovyExpression` cells.
 * Classpath, Method caches, and so forth are all scoped to tenant id.  When one is cleared, it does not clear cache for other tenants (`ApplicationID`s).
 * Removed unnecessary synchronization by using `ConcurrentHashMap`s.
 * JVM now handles proxied connections.
 * Many more tests have been added, getting code coverage to 95%.
* 2.9.18
 * Carrying ApplicationID throughout n-cube in preparation for n-cube 3.0.0.  This version is technically a pre-release candidate for 3.0.0.  It changes API and removes deprecated APIs.
* 2.9.17
 * Updated CSS tags in Html version of n-cube
 * bug fix: Removed StackOverflow that would occur if an n-cube cell referenced a non-existent n-cube.
 * n-cube names are now treated as case-retentive (case is ignored when locating them, however, original case is retained).
 * Improved unit test coverage.
* 2.9.16
 * Rule name is now displayed (if the 'name' meta-property on column is added) in the HTML.
 * Updated to use Groovy 2.3.7 up from 2.3.4
 * Added more exclusions to the CdnClassLoader to ensure that it does not make wasteful requests.
 * getCubeNames() is now available to Groovy cells to obtain the list of all n-cubes within the app (version and status).
* 2.9.15
 * Added getCube(), getAxis(), getColumn() APIs to NCubeGroovyExpression so that executing cells have easy access to these elements.
 * Added many n-cube classes and all of Java-util's classes as imports within NCubeGroovyExpression so that executing cells have direct access to these classes without requiring them to perform imports.
* 2.9.14
 * Required Scope and Optional Scope supported added.  Required scope is the minimal amount of keys (Set<String>) that must be present on the input coordinate in order to call ncube.getCell().  The n-cube meta property requiredScopeKeys can be set to a Groovy Expression (type="exp") in order to return a List of declared required scope keys.  Optional scope is computed by scanning all the rule conditions, and cells (and joining to other cubes) and including all of the scope keys that are found after 'input.'  The required scope keys are subtracted from this, and that is the full optional scope. Calls to ncube.setCell() need only the scope keys that the n-cube demands (values for Axes that do not have a Default column and are not Rule axes).  The declared required scope keys are not required for setCell(), removeCell(), or containsCell().
 * The 'RUN' feature (Tests) updated to use custom JSON written format, insulating the code from changes.
 * The 'RUN' feature obtains the Required Scope using the new Required Scope API.
* 2.9.13
 * The sys.classpath n-cube (one per app) now allows processing for loading .class files
 * Bug fix: not enough contrast between text URLs and background color on expression cells using URL to point to code.
* 2.9.12
 * Groovy classes from expression and method cells are compiled in parallel.
* 2.9.11
 * output.return entry added to output Map when getCell() / getCells() called.  The value associated to the key "return" is the value of the last step executed.  If it is a table of values, it is the value that was accessed.
* 2.9.8-2.9.10
 * Improvements in HTML display when a cell (or Column) has code in it.
* 2.9.7
 * Bug fix: Axis.updateColumns() should not have been processed (it is 'turned' on / off at Axis level).  This caused cells pointing to it to be dropped when the columns were edited.
* 2.9.6
 * The n-cube API that supports batch column editing (updateColumns()) has been updated to support all the proper parsing and range checking.
 * The HTML n-cube has been updated to include data-axis tags on the columns to support double-click column editing in NCE.
 * The top column row supports hover highlight.
* 2.9.5
 * SHA1 calculation of an n-cube is faster using a SHA1 MessageDigest instance directly.
 * Consolidated JsonFormatter / GroovyJsonFormatter into JsonFormatter.
 * Code moved from JsonFormatter to CellType Enum.
 * RuleInfo now a first-rate class. Dig into it (found on output map of getCell()) for rule execution tracing.
* 2.9.4
 * Rule execution tracing is complete, including calls to sub-rule cubes, sub-sub-rule cubes, etc.  It includes both 'begin>cubeName' and 'end>cubeName' markers as well as an entry for all rules that executed (condition true) in between.  If other rule cubes were called during rule execution, there execution traces are added, maintaining order.  The number of steps execution for a given rule set is kept, as well as all column bindings for each rule (indicates which columns pointed to the rule executed).
 * n-cube sha1() is now computed when formatted into JSON.  It is added as a meta-property on n-cube.  The SHA1 will be used along with NCE to determine if an n-cube has changed (basic for optimistic locking).
 * APIs added to generate test case input coordinates for all populated cells.
 * Improved HTML formatting for display in NCE (eventually NCE will do this in Javascript, and the JSON for the n-cube only will be sent to the client).
 * Code clean up related to formatting values and parsing values.
 * NCubeManager support for ApplicationID started, but not yet complete.
 * NCubeManager support for MongoDB started, but not yet complete.
 * MultiMatch flag removed from n-cube.  If you need multi-match on an axis, use a Rule axis.
* 2.9.3
 * SET and NEAREST axis values are now supported within Axis.convertStringToColumnValue().  This allows in-line editing of these values in the n-cube editor.
 * Many more tests added getting line coverage up to 96%.
 * NCube.setCellUsingObject() and NCube.getCellUsingObject() APIs removed.  Instead use NCube.objectToMap, and then call getCell() or getCells() with that Map.
* 2.9.2
 * jump() API added to expression cells.  Call jump() restarts the rule execution for the currently executing cube.  Calling jump([condition:ruleName, condition2:ruleName2, ...]) permits restarting the rule execution on a particular rule for each rule axis specified.
 * rule execution: If a rule axis is specified in the input coordinate (it is optional), then the associated value is expected to be a rule name (the 'name' field on a Column).  Execution for the rule axis will start at the specified rule.
 * Added new API to n-cube to fetch a List containing all required coordinates for each cell.  The coordinates are in terms of variable key names, not column ids.  This is useful for the n-cube Editor (GUI), allowing it to generate the Test Input coordinates for any cube.
 * N-cube hashcode() API was dramatically simplified.
 * Updated to use json-io 2.7.0
* 2.9.1
 * Added header 'content-type' for when CDN files are loaded locally from a developer's machine.  Providing the mime-type will quiet down browser warnings when loading Javascript, CSS, and HTML files.
 * Added new loadCubes() API to NCubeManager. This permits the caller to load all cubes for a given app, version, and status at start up, so that calls to other n-cubes from Groovy code will not have to worry about the other cubes being loaded.
 * Deprecated [renamed] NCubeManager.setBaseResourceUrls() to NCubeManager.addBaseResourceUrls().  It is additive, not replacing.
 * NCubeManager, Advice, Rules, and Axis tests have been separated into their own test classes, further reducing TestNCube class.
* 2.9.0
 * Bug fix: HTTP response headers are now copied case-insensitively to CdnRouter proxied HTTP response
 * New CdnDefaultHandler available for CDN content routers which dynamically adds logical file names to the CDN type specific routing cache.
* 2.8.2
 * Bug fix: CdnRouter now calls back to the CdnRoutingProvider on new API, doneWithConnection(Connection c) so that the provider knows that it can close or release the connection.
* 2.8.1
 * Bug fix: Exception handler in CdnRouter was chopping off the stack trace (only error message was getting reported).
 * Test case added for the case where an n-cube modifies itself from within execution of getCell().  The example has an axis where the cell associated to the default Column on an axis adds the sought after column.  It's akin to a Map get() call that does a put() of the item if it is not there.
* 2.8.0
 * Rule Execution: After execution of cube that had one or more rule axes (call to getCells()), the output Map has a new entry added under the "_rule" section, named "RULES_EXECUTED".  This entry contains the names (or ID if no name given) of the condition(s) and the return value of the associated statement. More than one condition could be associated to a statement in the case of a cube with 2 or more rule axes.  Therefore the keys of the Map are List, and the value is the associated statement return value.
 * Rule Execution: A condition's truth (true or false) value follows the same as the Groovy language.  For details, see http://www.groovy-lang.org/semantics.html#Groovy-Truth
 * Java 1.7: Template declarations updated to Java 1.7 syntax (no need to repeat collection template parameters a 2nd time on the RHS).
* 2.7.5
 * Added ability to turn Set<Long> into Map<String, Object> coordinate that will retrieve cell described by Set<Long>.  Useful for n-cube editor.
* 2.7.4
 * Bug fix: reloading n-cubes now clears all of its internal caches, thereby allowing reloading Groovy code without server restarts.
 * Bug fix: NCubeManager was not rethrowing the exception when a bad URL was passed to setBaseResourceUrls().
 * Bug fix: If a URL failed to resolve (valid URL, but nothing valid at the other end of the URL), an NPE occurred.  Now an exception is thrown indicating the URL that failed to resolve, the n-cube it resided within, and the version of the n-cube.
* 2.7.3
 * Added GroovyClassLoader 'file' version so that the .json loaded files do not need to call NCubeManager.setUrlClassLoader()
* 2.7.2
 * New API added to NCubeManager, doesCubeExist(), which returns true if the given n-cube is stored within the persistent storage.
 * HTML-syntax highlighting further improved
* 2.7.1
 * Dynamically loaded Groovy classes (loaded from URL), load much faster.
 * The HTML representation of n-cube updated to differentiate URL specified cells and expression cells, from all other cells.  Very basic syntax highlighting if you can call it that.
* 2.7.0
 * New capability: key-value pairs can be added to n-cube, any axis, and any column.  These are picked up from the JSON format or set via setMetaProperty() API.  This allows you to add additional information to an ncube, it's axis, or a column, and it will be stored and retrieved with the n-cube and can be queried later.
 * New capability: NCubeManager has a new API, setUrlClassLoader() which allows you to set a List of String URLs to be added to the Groovy class path that is used when a class references another class by import, extends, or implements.  The URL should point to the fully qualified location up to but just before the code resource (don't include the /com/yourcompany/... portion).
 * New capability: n-cube can be used as a CDN router.  Used in this fashion, fetches to get content will be routed based on the scope of the cube used to route CDN requests.  In order to use this feature, it is expected you have a servlet filter like urlRewrite from Tuckey, which will forward requests to the CdnRouter class.  Furthermore, you need to set up a CdnRouterProvider.  See the code / comments in the com.cedarsoftware.ncube.util package.
* 2.6.3
 * CdnUrlExecutor updated to handle Classpath for resolving URL content (in addition to the existing HTTP support).
* 2.6.2
 * GroovyShell made static in GroovyBase.  GroovyShell is re-entrant.  The GroovyShell is only used when parsing CommandCell URLs to allow for @refCube[:] type expansions.
* 2.6.1
 * Refactor CommandCell interface to have fewer APIs.
 * Bug fix: null check added for when 'Command Text' is empty and attempting search for referenced cube names within it.
* 2.6.0
 * An Executor can be added to a call to getCell(), getCells() where the Executor will be called instead of fetching the cell.  The defaultCellExecutor will execute the cell as before.  It can be overridden so external code can be executed before the cell is returned or after it is executed.
 * runRuleCube() API added to the NCubeGroovyExpression so that a rule can run other rules in other cubes.
 * Potential concurrency bug fixed if the URL: feature of an n-cube was used, and the cell content was not cached.
* 2.5.0
 * Advice can be specified to cube and method name, using wildcards.  For example, `*Controller.save*()` would add the advice to all n-cube Controller classes methods that start with `save`.
 * `containsCellValue()` API added to NCube.  This will return `true` if, and only if, the cell specified by the coordinate has an actual value located in it (defaultCellValue does not count).
 * `containsCell()` API changed.  It will return `true` if the cell has a value, including the defaultCellValue, if it is not null.
 * Both `containsCell()` and `containsCellValue()` throw `CoordinateNotFoundException` if the specified coordinate falls outside the n-cube's defined hyper-space.
 * New public API added to Axis, `promoteValue(AxisValueType type, Comparable value)`.  If the value passed in (e.g. an int) is of the same kind as AxisValueType (e.g. long) then the returned value will be the the larger AxisValueType (long in this example).  If the `valueType` is not of the same basic nature as `value`, an intelligent conversion will be done.  For example, String to Date, Calendar to Date, Date to String, Long to String, Integer to String, String to BigDecimal, and so on.
 * When a Rule cube executes, the output map always contains an `_rule` entry.  There is a constant now defined on NCube (`RULE_EXEC_INFO`) which is used to fetch this meta-information Map.  A new Enum has been added, `RuleMetaKeys` which contains an enum for each rule-meta information entry in this Map.
* 2.4.0
 * Advice interface added.  Allows before() and after() methods to be called before Controller methods or expressions are called.  Only expressions specified by 'url' can have advice placed around them.
 * NCube now writes 'simple JSON' format.  This is the same JSON format that is used in the test resources .json files.  NCubes stored in the database are now written in simple JSON format.  This insulates the format from member variable changes to n-cube and its related classes.
 * Expression (Rule) axis - now supports default column.  This column is fired if no prior expressions fired.  It is essentially the logical NOT of all of the expressions on a rule axis.  Makes it trivial to write the catch-if-nothing-fired-condition.
 * Expression (Rule) axis - conditions on Rule axis can now be specified with URL to the Groovy condition code.  This allows single-step debugging of lengthy conditions.
 * URL specified commands can use res:// URLs for indicating that the source is located within the Java classpath, or http(s).
 * URL specified commands can use @cube[:] references within the URL to allow the hostname / protocol / port to be indicated elsewhere, insulating URLs from changing when the host / protocol / port needs to be changed.
 * NCube methods and expressions specified by URL can now be single-stepped.
 * In the simple JSON format, type 'array' is no longer supported.  To make a cell an Object[], List, Map, etc., make the cell type 'exp' and then specify the content in List, Map, Object[], etc.  For an Object[] use "[1, 2, 3] as Object[]". For a List, use "['This', 'is', 'a list']".  For a Map, use "[key1:'Alpha', key2:'Beta']"
 * Expression cells or controller methods that have identical source code, will use the same Groovy class internally.  The source code is SHA1 hashed and keyed by the hash internally.
* 2.3.3
 * RenameNCube() API added to NCubeManager.
 * The regex that locates the relative n-cube references e.g. @otherCube[x:val], improved to no longer incorrectly find annotations as n-cube references.
 * Levenshtein algorithm moved to CedarSoftware's java-util library. N-cube already had a dependence on java-util.
* 2.3.2
 * HTML formatting improved to handle all cell data types
 * Parse routine that fetches n-cube names was matching too broad a string for n-cube name.
* 2.3.1
 * Date axis be created from, or matched with, a String that passed DateUtilities.parseDate().
 * String axis can be created from, or matched with, a Number instance.
 * Axis.promoteValue() has been made public.
* 2.3.0
 * Groovy expression, method, and template cells can be loaded from 'url' instead of having content directly in 'value' field.  In addition, the 'cacheable' attribute can be added.  When 'true', the template, expression, or method, is loaded and compiled once, and then stored in memory. If 'cacheable' attribute is 'false', then the content is retrieved on each access.
 * Within the url, other n-cubes can be referenced.  For example, @settings[:]/html/index.html.  In this example, the current input coordinate that directed access to the cell containing the URL reference, is passed as input to the referenced n-cube(s).  This allows a 'settings-type' n-cube to be used to keep track of actual domains, ports, contexts, etc., leaving the URLs in all the other cubes not needed to be changed when the domain, port, etc. is changed.
* 2.2.0
 * Axis update column value(s) support added
 * NCubeInfoDto ncube id changed from long to String
 * NCubeManager now caches n-cube by name and version, allowing two or more versions of the same named n-cube to be loaded at the same time.  Useful in multi-tenant environment.
 * NCube has the version it was loaded from 'stamped' into it (whether file or disk loaded). Use n-ube's getVersion() API to retrieve it.
* 2.1.0
 * Rule conditions and statements can stop rule execution.  ruleStop() can be called from the condition column or from a Groovy expression or method cell.
 * Output Map is written to in the '_rule' key of the output map, which is a Map, with an entry to indicate whether or not rules where stopped prematurely.  In the future, other useful rule execution will be added to this map.
 * id's specified in simple JSON format can be long, string, double, or boolean. Allows aliasing columns to be referenced by cells' id field.
 * HTML formatting code moved into separate internal formatters package, where other n-cube formatters would be placed.
* 2.0.1
 * 'binary' type added to simple JSON format.  Marks a cell to be returned as byte[].  'value' should be set to hex digits 'CAFEBABE10', or the 'url' should be set to point to location returning binary content.
 * 'cacheable' flag added to 'string' and 'binary' cells (when specified as 'url').  If not specified, the default is cacheable=true, meaning that n-cube will fetch the contents from the URL, and then hold onto it.  Set to "cacheable":false and n-cube will retrieve the content each time the cell is referenced.
* 2.0.0
 * Initial version

By: John DeRegnaucourt
