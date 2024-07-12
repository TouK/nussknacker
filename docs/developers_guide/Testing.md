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
    .emptySink("end", "sink", "value" -> "#customer")
```

### Creating test scenario runner

Scenario should be executed inside a runner. `TestScenarioRunner` gives you another DLS for building runners.
At first, you chose type of the scenario from:
- `flinkBased` - based on Flink engine, you need to pass to it `FlinkMiniClusterHolder`, it can be created e.g. using `FlinkSpec`:

```scala
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
val testScenarioRunner = TestScenarioRunner
  .flinkBased(config, flinkMiniCluster)
  .build()
```

- `kafkaLiteBased` - based on Lite engine, no other setup needed, provides suitable methods of simulation communication with kafka, it bases on mocked Schema Registry, Kafka server is not needed:
```scala
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner._
val testScenarioRunner = TestScenarioRunner
  .kafkaLiteBased()
  .build()
```

- `liteBased` - also based on Lite engine, provides interface to communicate with engine using raw classes (not using any Kafka API, similar interface as using flinkBased):
```scala
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
val testScenarioRunner = TestScenarioRunner
  .liteBased()
  .build()
```

### Injecting custom and mocked components
You can inject list of your own or mocked components with `.withExtraComponents` method on specified above `TestScenarioRunner` to be passed to engine model data.
Each component should match `ComponentDefinition`. It means that ComponentDefinition passed via `.withExtraComponents` overrides the one which could be already defined in modelData for given name. Example:
```scala
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
val stubbedGetCustomerOpenApiService: SwaggerEnricher = new SwaggerEnricher(Some(new URL(rootUrl(port))), services.head, Map.empty, stubbedBackedProvider)
val mockComponents = List(ComponentDefinition("getCustomer", stubbedGetCustomerOpenApiService))
val testScenarioRunner = TestScenarioRunner
      .flinkBased(resolvedConfig, flinkMiniCluster)
      .withExtraComponents(mockComponents)
      .build()
```

### Injecting custom global variables
You can inject map of your own or mocked UDFs (user defined functions) or other variables using `.withExtraGlobalVariables` on specified above `TestScenarioRunner` to be passed to engine model data. Example:

```scala
import pl.touk.nussknacker.engine.util.functions._
val now = Instant.now()
val mockedClock = Clock.fixed(now, ZoneId.systemDefault())
val mockedDate = new DateUtils(mockedClock)
val globalVariables = Map("DATE" -> date)

val testScenarioRunner = TestScenarioRunner
      .flinkBased(resolvedConfig, flinkMiniCluster)
      .withExtraGlobalVariables(globalVariables)
      .build()
```

### Running scenario with data

Scenario can be run with data via `.runWithData` method. This call synchronously executes scenario inside runner with data being passed to input source.

```scala
testScenarioRunner.runWithData(scenario, List(1, 3, 5))
```

Both `flinkBased` and `liteBased` scenario test runners provides additional `source` component which is used for providing test data in `runWithData` method.
Results are collected using `sink` component in `liteBased` case and `invocationCollector` in `flinkBased` case.
All component names can be accessed using `TestScenarioRunner` object e.g. using `TestScenarioRunner.testDataSource` property.

In case of `kafkaLiteBased` scenario test runner, you should use the same source/sink components as in production (e.g. `kafka`). There are available
methods for passing Avro records or JSON objects - you don't need to serialize them. Example for Avro:

```scala
val runner = TestScenarioRunner.kafkaLiteBased().build()
val sourceSchemaId = runner.registerAvroSchema("sourceTopic", sourceSchema)
runner.registerAvroSchema("sinkTopic", sinkSchema)

val genericRecord = new GenericRecordBuilder(sourceSchema).set("field", "value").build()
val input = KafkaAvroConsumerRecord("sourceTopic", genericRecord, sourceSchemaId)
runner.runWithAvroData(scenario, List(input))
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
