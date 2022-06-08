package pl.touk.nussknacker.engine.lite.components

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.{KafkaConsumerRecord, KafkaTestScenarioRunner, LiteKafkaAvroTestScenarioRunner}

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers {

  import KafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val RecordSchemaString: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" },
      |    { "name": "age", "type": "int" }
      |  ]
      |}
    """.stripMargin

  private val inputTopic = "input"
  private val outputTopic = "output"

  test("should test end to end kafka avro sink / source") {
    //Given
    val scenario = ScenarioBuilder.streamingLite("check avro serialization")
      .source("my-source", SourceName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
      .emptySink("my-sink", SinkName, TopicParamName -> s"'$outputTopic'",  SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "", SinkValueParamName -> s"#input", SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'")

    val runtime = LiteKafkaAvroTestScenarioRunner(new LiteKafkaSourceImplFactory, LiteKafkaAvroSinkImplFactory)
    runtime.registerSchema(inputTopic, RecordSchemaString)
    runtime.registerSchema(outputTopic, RecordSchemaString)

    //When
    val input = KafkaConsumerRecord(inputTopic, Map("first" -> "Jan", "last" -> "Kowalski", "age" -> 18))
    val result = runtime.runWithResultValue(scenario, List(input))

    //Then
    result shouldBe List(input.value())
  }
}
