package pl.touk.nussknacker.engine.avro.source.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json._
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess._
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.avro.schema.Address
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaRegistryProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, InputMetaToJson}
import pl.touk.nussknacker.engine.process.ProcessToString.marshall
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.util.Collections

class TestFromFileSpec extends FunSuite with Matchers with LazyLogging {

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider: SchemaRegistryProvider =
      ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
  }

  private lazy val config = ConfigFactory.empty()
    .withValue("kafka.kafkaAddress", fromAnyRef("notused:1111"))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("notused:2222"))

  test("Should pass correct timestamp from test data") {

    val topic = "simple"
    val expectedTimestamp = System.currentTimeMillis()
    val inputMeta = InputMeta(null, topic, 0, 1, expectedTimestamp, TimestampType.CREATE_TIME, Collections.emptyMap(), 0)
    val id: Int = registerSchema(topic)

    val process = EspProcessBuilder.id("test")
      .source(
        "start", "kafka-avro", TopicParamName -> s"'$topic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      ).customNode("transform", "extractedTimestamp", "extractAndTransformTimestamp", "timestampToSet" -> "0L")
      .emptySink("end", "sinkForInputMeta", "value" -> "#inputMeta")

    val consumerRecord = new InputMetaToJson()
      .encoder(BestEffortJsonEncoder.defaultForTests).apply(inputMeta)
      .mapObject(_.add("key", Null)
      .add("value", obj("city" -> fromString("Lublin"), "street" -> fromString("Lipowa"))))

    val results = run(process, TestData.newLineSeparated(s"""{"keySchemaId":null,"valueSchemaId":$id,"consumerRecord":${consumerRecord.noSpaces} }"""))

    val testResultVars = results.nodeResults("end").head.context.variables
    testResultVars.get("extractedTimestamp") shouldBe Some(expectedTimestamp)
    testResultVars.get("inputMeta") shouldBe Some(inputMeta)
  }

  private def registerSchema(topic: String) = {
    val subject = ConfluentUtils.topicSubject(topic, isKey = false)
    val parsedSchema = ConfluentUtils.convertToAvroSchema(Address.schema)
    schemaRegistryMockClient.register(subject, parsedSchema)
  }

  private def run(process: EspProcess, testData: TestData): TestResults[Any] = {

    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(LocalModelData(config, creator),
        marshall(process), testData, FlinkTestConfiguration.configuration(), identity)
    }

  }

}
