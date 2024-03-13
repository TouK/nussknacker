package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.parser.parse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class UniversalSourceAvroSchemaLiteTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val inputTopic  = "input"
  private val outputTopic = "output"

  private val schema = AvroUtils.parseSchema(s"""{
       |  "type": "record",
       |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
       |  "name": "FullName",
       |  "fields": [
       |    { "name": "first", "type": "string" },
       |    { "name": "last", "type": "string" },
       |    { "name": "age", "type": "int" }
       |  ]
       |}
    """.stripMargin)

  private val scenario = ScenarioBuilder
    .streamingLite("check json serialization")
    .source(
      "my-source",
      KafkaUniversalName,
      TopicParamName.value         -> s"'$inputTopic'",
      SchemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'"
    )
    .emptySink(
      "my-sink",
      KafkaUniversalName,
      TopicParamName.value         -> s"'$outputTopic'",
      SchemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'",
      SinkKeyParamName.value       -> "",
      SinkRawEditorParamName.value -> "false",
      "first"                      -> s"#input.first",
      "last"                       -> "#input.last",
      "age"                        -> "#input.age"
    )

  test("should read data with json payload on avro schema based topic") {
    // Given
    val config = ConfigFactory
      .load()
      .withValue("kafka.avroAsJsonSerialization", fromAnyRef(true))
    val runner = TestScenarioRunner.kafkaLiteBased(config).build()
    runner.registerAvroSchema(inputTopic, schema)
    runner.registerAvroSchema(outputTopic, schema)

    // When
    val jsonRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val input = new ConsumerRecord(inputTopic, 1, 1, null.asInstanceOf[Array[Byte]], jsonRecord)

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result                                               = runner.runWithRawData(scenario, list).validValue
    val resultWithValue                                      = result.copy(successes = result.successes.map(_.value()))

    // Then
    resultWithValue.errors shouldBe empty
    resultWithValue.successes should not be empty
    val resultJson = new String(resultWithValue.successes.head)
    val expected   = new String(jsonRecord)
    parse(expected) shouldBe parse(resultJson)
  }

}
