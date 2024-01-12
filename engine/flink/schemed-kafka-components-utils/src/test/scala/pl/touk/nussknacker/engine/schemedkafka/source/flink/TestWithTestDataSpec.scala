package pl.touk.nussknacker.engine.schemedkafka.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json._
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, InputMetaToJson}
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  SchemaVersionParamName,
  TopicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schema.Address
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.test.KafkaConfigProperties
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.source.flink.TestWithTestDataSpec.sinkForInputMetaResultsHolder

import java.util.Collections

class TestWithTestDataSpec extends AnyFunSuite with Matchers with LazyLogging {

  private lazy val creator: KafkaAvroTestProcessConfigCreator =
    new KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {
      override protected def schemaRegistryClientFactory =
        MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    }

  private lazy val config = ConfigFactory
    .empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("notused:1111"))
    .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef("notused:2222"))
    .withValue("kafka.avroKryoGenericRecordSchemaIdSerialization", fromAnyRef(false))

  test("Should pass correct timestamp from test data") {

    val topic             = "simple"
    val expectedTimestamp = System.currentTimeMillis()
    val inputMeta =
      InputMeta(null, topic, 0, 1, expectedTimestamp, TimestampType.CREATE_TIME, Collections.emptyMap(), 0)
    val id: Int = registerSchema(topic)

    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        TopicParamName         -> s"'$topic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L")
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta")

    val consumerRecord = new InputMetaToJson()
      .encoder(BestEffortJsonEncoder.defaultForTests.encode)
      .apply(inputMeta)
      .mapObject(
        _.add("key", Null)
          .add("value", obj("city" -> fromString("Lublin"), "street" -> fromString("Lipowa")))
      )
    val testRecordJson = obj("keySchemaId" -> Null, "valueSchemaId" -> fromInt(id), "consumerRecord" -> consumerRecord)
    val scenarioTestData = ScenarioTestData(ScenarioTestJsonRecord("start", testRecordJson) :: Nil)

    val results = run(process, scenarioTestData)

    val testResultVars = results.nodeResults("end").head.variables
    testResultVars.get("extractedTimestamp") shouldBe Some(expectedTimestamp)
    testResultVars.get("inputMeta") shouldBe Some(inputMeta)
  }

  test("Should pass parameters correctly and use them in scenario test") {

    val topic = "simple"
    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        TopicParamName         -> s"'$topic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L")
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#input.city + '-' + #input.street")

    val parameterExpressions: Map[String, Expression] = Map(
      "city"   -> Expression.spel("'Lublin'"),
      "street" -> Expression.spel("'Lipowa'"),
    )
    val scenarioTestData = ScenarioTestData("start", parameterExpressions)

    val results = run(process, scenarioTestData)
    results.invocationResults("end").head.value shouldBe "Lublin-Lipowa"

  }

  test("should handle fragment test parameters in test") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'")
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in")

    val parameterExpressions: Map[String, Expression] = Map(
      "in" -> Expression.spel("'some-text-id'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = run(fragment, scenarioTestData)

    results.nodeResults("fragment1") shouldBe List(
      Context("fragment1-fragment1-0-0", Map("in" -> "some-text-id"))
    )
    results.nodeResults("fragmentEnd") shouldBe List(
      Context("fragment1-fragment1-0-0", Map("in" -> "some-text-id", "out" -> "some-text-id"))
    )
    results.invocationResults("fragmentEnd") shouldBe List(
      ExpressionInvocationResult("fragment1-fragment1-0-0", "out", "some-text-id")
    )
    results.exceptions shouldBe empty
  }

  private def registerSchema(topic: String) = {
    val subject      = ConfluentUtils.topicSubject(topic, isKey = false)
    val parsedSchema = ConfluentUtils.convertToAvroSchema(Address.schema)
    schemaRegistryMockClient.register(subject, parsedSchema)
  }

  private def run(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults = {
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(
        LocalModelData(config, List.empty, configCreator = creator),
        process,
        scenarioTestData,
        FlinkTestConfiguration.configuration()
      )
    }
  }

}

object TestWithTestDataSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[InputMeta[_]]

}
