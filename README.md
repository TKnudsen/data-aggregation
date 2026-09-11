# data-aggregation

Bin-based data aggregation, contingency tables, and interestingness measures for exploratory
data analysis.

## Packages

- `com.github.TKnudsen.dataAggregation.data.bins` -- `Bin` and its numeric/discrete/string
  implementations, used to partition attribute values into buckets.
- `com.github.TKnudsen.dataAggregation.data.aggregation` -- `Aggregation`, a set of items grouped
  by shared bin membership across one or more attributes.
- `com.github.TKnudsen.dataAggregation.data.contingencyTable` -- `ContingencyTable` and
  `AttributeRankingOption` for cross-tabulating two attributes' aggregations.
- `com.github.TKnudsen.dataAggregation.operation.aggregation` -- calculation, characterization,
  and executor classes that turn raw data into `Aggregation`s.
- `com.github.TKnudsen.dataAggregation.operation.contingencyTable` -- builds and caches
  `ContingencyTable`s from aggregations.
- `com.github.TKnudsen.dataAggregation.operation.interestingness` -- measures for ranking how
  "interesting" (statistically noteworthy) a contingency table or aggregation is: chi-square,
  mutual information, phi coefficient, signed phi coefficient.
- `com.github.TKnudsen.dataAggregation.operation.distanceMeasures` -- distance measures between
  bins/aggregations.
- `com.github.TKnudsen.dataAggregation.operation.optimalBinning` -- bin-selection heuristics.
- `com.github.TKnudsen.dataAggregation.control` -- headless selection/highlight orchestration
  (`de.javagl.selection`-based) for wiring aggregation state to a view layer, without depending
  on one.

## Tester classes

`com.github.TKnudsen.dataAggregation.test` holds small `main()`-based demo/tester classes rather
than a JUnit suite (that's a known gap -- contributions welcome). Some exercise the bundled
sample datasets under `src/test/resources/data` (`cars.arff`, `titanic.txt`,
`titanic_extended.txt`).

## Dependencies

Builds on `com.github.tknudsen:complex-data-object` and `com.github.tknudsen:statistics` for
data model and statistical primitives, plus Apache Commons Math/Collections, Jackson, and
`de.javagl:selection`. No UI framework dependency.

## Maven coordinates

```xml
<dependency>
    <groupId>com.github.tknudsen</groupId>
    <artifactId>data-aggregation</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Currently resolvable only via a local `mvn install` of this repository (not yet deployed to a
public snapshot repository).

## License

Apache License 2.0 -- see [LICENSE](LICENSE).
