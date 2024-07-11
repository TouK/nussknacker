package pl.touk.nussknacker.engine.lite.components

import org.everit.json.schema.{CombinedSchema, NullSchema, NumberSchema, Schema}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider._
import pl.touk.nussknacker.engine.lite.util.test.KafkaConsumerRecord
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner.LiteKafkaTestScenarioRunnerExt
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class KafkaJsonRefFunctionalSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private val runner = TestScenarioRunner.kafkaLiteBased().build()

  test("filled record after empty record with schema using refs") {
    val inputTopic  = TopicName.ForSource("schema-using-refs-input")
    val outputTopic = TopicName.ForSink("schema-using-refs-output")
    runner.registerJsonSchema(inputTopic.toUnspecialized, RecordWithRef.jsonSchemaUsingRefs)
    runner.registerJsonSchema(
      outputTopic.toUnspecialized,
      CombinedSchema.anyOf(List(NumberSchema.builder().build(), NullSchema.INSTANCE).asJava).build()
    )
    val scenario = createScenario(inputTopic, outputTopic)
    val givenRecords =
      List(RecordWithRef.emptyExampleJson, RecordWithRef.simpleRecordExampleJson).map(
        KafkaConsumerRecord[String, String](inputTopic, _)
      )

    val result = runner.runWithStringData(scenario, givenRecords).validValue
    result.errors shouldBe empty
    result.successes.map(_.value()) shouldEqual List("null", RecordWithRef.exampleValue.toString)
  }

  test("filled record after empty record with schema with inlined refs") {
    val inputTopic  = TopicName.ForSource("schema-with-inlined-refs-input")
    val outputTopic = TopicName.ForSink("schema-with-inlined-refs-output")
    runner.registerJsonSchema(inputTopic.toUnspecialized, RecordWithRef.jsonSchemaWithInlinedRefs)
    runner.registerJsonSchema(
      outputTopic.toUnspecialized,
      CombinedSchema.anyOf(List(NumberSchema.builder().build(), NullSchema.INSTANCE).asJava).build()
    )
    val scenario = createScenario(inputTopic, outputTopic)
    val givenRecords =
      List(RecordWithRef.emptyExampleJson, RecordWithRef.simpleRecordExampleJson).map(
        KafkaConsumerRecord[String, String](inputTopic, _)
      )

    val result =
      runner.runWithStringData(scenario, givenRecords).validValue

    result.errors shouldBe empty
    result.successes.map(_.value()) shouldEqual List("null", RecordWithRef.exampleValue.toString)
  }

  private def createScenario(inputTopic: TopicName.ForSource, outputTopic: TopicName.ForSink): CanonicalProcess = {
    import pl.touk.nussknacker.engine.spel.SpelExtension._
    ScenarioBuilder
      .streaming(classOf[KafkaJsonRefFunctionalSpec].getSimpleName)
      .parallelism(1)
      .source(
        "source",
        KafkaUniversalName,
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
      )
      .emptySink(
        "sink",
        KafkaUniversalName,
        topicParamName.value              -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value            -> "".spel,
        sinkValueParamName.value          -> s"#input.field".spel,
        sinkRawEditorParamName.value      -> "true".spel,
        sinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'".spel
      )
  }

}

object RecordWithRef {

  def jsonSchemaUsingRefs: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "$defs": {
      |    "SomeRef": {
      |      "anyOf": [
      |        {
      |          "type": "integer"
      |        },
      |        {
      |          "type": "null"
      |        }
      |      ],
      |      "default": null
      |    }
      |  },
      |  "properties": {
      |    "field": {
      |      "$ref": "#/$defs/SomeRef"
      |    }
      |  }
      |}""".stripMargin
  )

  def jsonSchemaWithInlinedRefs: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "field": {
      |      "default": null,
      |      "anyOf": [
      |        {
      |          "type": "integer"
      |        },
      |        {
      |          "type": "null"
      |        }
      |      ]
      |    }
      |  }
      |}""".stripMargin
  )

  val emptyExampleJson: String = "{}"

  val exampleValue = 123

  val simpleRecordExampleJson: String =
    s"""{
       |  "field": $exampleValue
       |}""".stripMargin

}
