# Testing

:::caution

Testing API should not be considered stable at the moment.

:::

Additional components need to be tested before they can be used by the users. Nussknacker provides toolkit for building scenarios and executing them in tests. There are two main dependencies you can add in scope `test` 

- `nussknacker-flink-components-testkit`
- `nussknacker-lite-components-testkit`

These modules will provide you with specific executor and test components building blocks as well as with [Scalatest](https://www.scalatest.org/user_guide/writing_your_first_test) and utilities.

## Testing Nussknacker Components

### Creating test scenario
There is a `ScenarioBuilder` which gives developer DSL-like mechanism for building scenarios. 
At first, you choose type of the scenario from:
- streaming
- streamingLite
- requestResponse

Next you specify scenario itself. Every scenario need at least one `source` followed by filters, enrichers etc. and end with either sink or processor end. 

```scala
val scenario = 
  ScenarioBuilder
    .streaming("openapi-test")
    .parallelism(1)
    .source("start", "source")
    .enricher("customer", "customer", "getCustomer", ("customer_id", "#input"))
    .processorEnd("end", "invocationCollector", "value" -> "#customer")
```

### Creating test scenario runner
Scenario should be executed inside a runner. `NuTestScenarioRunner` gives you another DLS for building runners.
At first, you chose type of the scenario from:
- flink - only one right now

```scala
 val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()
```

:::caution
For flink runner test must extend FlinkSpec for now.
:::

### Injecting custom and mocked components
You can inject list of your own or mocked components with `.withExtraComponents` method on `NuTestScenarioRunner` to be passed to engine model data.
Each component should match `ComponentDefinition`. It means that ComponentDefinition passed via `.withExtraComponents` overrides the one which could be already defined in modelData for given name. Example:
```scala
val stubbedGetCustomerOpenApiService: SwaggerEnricher = new SwaggerEnricher(Some(new URL(rootUrl(port))), services.head, Map.empty, stubbedBackedProvider)
val mockComponents = List(ComponentDefinition("getCustomer", stubbedGetCustomerOpenApiService))
val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(resolvedConfig, flinkMiniCluster)
      .withExtraComponents(mockComponents)
      .build()
```

### Running scenario with data
Scenario can be run with data via `.runWithData` method. This call synchronously executes scenario inside runner with data being passed to input source.

```scala
testScenarioRunner.runWithData(scenario, List(1, 3, 5))
```
### Retrieving results

Results of the scenario invocation can be get with `results()`

```scala
testScenarioRunner.results().size shouldBe 6
testScenarioRunner.results().toSet shouldBe Set(5, 10, 15, 20, 30, 40)
```

## Auto provided test components
Test toolkit automatically gives you few test components you could see above.
- source - it can be used to provide data for the scenario
- invocationCollector - you can use it to verify scenario behaviour
