package pl.touk.nussknacker.engine.schemedkafka.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.Json._
import org.apache.avro.Schema
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{LoneElement, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schema.{Address, Company}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClientFactory, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.source.flink.TestWithTestDataSpec.sinkForInputMetaResultsHolder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, KafkaConfigProperties}

import java.util.Collections

class TestWithTestDataSpec
    extends AnyFunSuite
    with Matchers
    with LazyLogging
    with EitherValuesDetailedMessage
    with OptionValues
    with LoneElement {

  private lazy val creator: KafkaAvroTestProcessConfigCreator =
    new KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {
      override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
        MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    }

  private lazy val config = ConfigFactory
    .empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("kafka_should_not_be_used:9092"))
    .withValue(
      KafkaConfigProperties.property("schema.registry.url"),
      fromAnyRef("schema_registry_should_not_be_used:8081")
    )
    .withValue("kafka.topicsExistenceValidationConfig.enabled", fromAnyRef(false))
    .withValue("kafka.avroKryoGenericRecordSchemaIdSerialization", fromAnyRef(false))

  test("Should pass correct timestamp from test data") {
    val topic             = UnspecializedTopicName("address")
    val expectedTimestamp = System.currentTimeMillis()
    val inputMeta = InputMeta(
      key = null,
      topic = topic.name,
      partition = 0,
      offset = 1,
      timestamp = expectedTimestamp,
      timestampType = TimestampType.CREATE_TIME,
      headers = Collections.emptyMap(),
      leaderEpoch = 0
    )
    val inputMetaAsJson = Json.fromFields(
      Map(
        "key"           -> Json.Null,
        "topic"         -> Json.fromString(topic.name),
        "partition"     -> Json.fromInt(0),
        "offset"        -> Json.fromInt(1),
        "timestamp"     -> Json.fromLong(expectedTimestamp),
        "timestampType" -> Json.fromString("CreateTime"),
        "headers"       -> Json.fromFields(List.empty),
        "leaderEpoch"   -> Json.fromInt(0)
      )
    )
    val id: Int = registerSchema(topic, Address.schema)

    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L".spel)
      .emptySink("end", "sinkForInputMeta", SingleValueParamName -> "#inputMeta".spel)

    val consumerRecord = ToJsonEncoder.defaultForTests
      .encode(inputMeta)
      .mapObject(
        _.add("value", obj("city" -> fromString("Lublin"), "street" -> fromString("Lipowa")))
      )
    val testRecordJson = obj("keySchemaId" -> Null, "valueSchemaId" -> fromInt(id), "consumerRecord" -> consumerRecord)
    val scenarioTestData = ScenarioTestData(ScenarioTestJsonRecord("start", testRecordJson) :: Nil)

    val results = run(process, scenarioTestData)

    val testResultVars = results.nodeResults("end").head.variables
    testResultVars("extractedTimestamp").hcursor.downField("pretty").as[Long].rightValue shouldBe expectedTimestamp
    testResultVars("inputMeta").hcursor.downField("pretty").focus.value shouldBe inputMetaAsJson
  }

  test("Should pass parameters correctly and use them in scenario test") {
    val topic = UnspecializedTopicName("company")
    registerSchema(topic, Company.schema)
    val process = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "kafka",
        topicParamName.value         -> s"'${topic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L".spel)
      .emptySink(
        "end",
        "sinkForInputMeta",
        SingleValueParamName -> "#input.name + '-' + #input.address.city + '-' + #input.address.street".spel
      )

    val parameterExpressions = Map(
      ParameterName("name")           -> Expression.spel("'TouK'"),
      ParameterName("address.city")   -> Expression.spel("'Warszawa'"),
      ParameterName("address.street") -> Expression.spel("'Bohaterów Września'"),
    )
    val scenarioTestData = ScenarioTestData("start", parameterExpressions)

    val results = run(process, scenarioTestData)
    results
      .invocationResults("end")
      .head
      .value
      .hcursor
      .downField("pretty")
      .as[String]
      .rightValue shouldBe "TouK-Warszawa-Bohaterów Września"

  }

  test("should handle fragment test parameters in test") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'".spel)
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)

    val parameterExpressions = Map(
      ParameterName("in") -> Expression.spel("'some-text-id'")
    )
    val scenarioTestData = ScenarioTestData("fragment1", parameterExpressions)
    val results          = run(fragment, scenarioTestData)

    results.nodeResults("fragment1").loneElement shouldBe ResultContext(
      "fragment1-fragment1-0-0",
      Map(
        "in" -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
      )
    )

    results.nodeResults("fragmentEnd").loneElement shouldBe ResultContext(
      "fragment1-fragment1-0-0",
      Map(
        "in"  -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id"))),
        "out" -> Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
      )
    )

    results.invocationResults("fragmentEnd").loneElement shouldBe ExpressionInvocationResult(
      "fragment1-fragment1-0-0",
      "out",
      Json.fromFields(Seq("pretty" -> Json.fromString("some-text-id")))
    )

    results.exceptions shouldBe empty
  }

  private def registerSchema(topic: UnspecializedTopicName, schema: Schema) = {
    val subject      = ConfluentUtils.topicSubject(topic, isKey = false)
    val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
    schemaRegistryMockClient.register(subject, parsedSchema)
  }

  private def run(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] = {
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(
        LocalModelData(config, List.empty, configCreator = creator),
        process,
        scenarioTestData,
        FlinkTestConfiguration.configuration(),
      )
    }
  }

}

object TestWithTestDataSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}
