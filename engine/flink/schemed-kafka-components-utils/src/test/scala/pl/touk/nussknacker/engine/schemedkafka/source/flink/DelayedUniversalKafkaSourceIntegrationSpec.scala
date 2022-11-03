package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.avro.generic.GenericRecord
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.kafka.generic.FlinkKafkaDelayedSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory.{DelayParameterName, TimestampFieldParamName}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, TopicParamName, SinkValueParamName}
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.LongFieldV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.source.delayed.DelayedUniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.time.Instant

class DelayedUniversalKafkaSourceIntegrationSpec extends AnyFunSuite with KafkaAvroSpecMixin with BeforeAndAfter  {

  private lazy val creator: ProcessConfigCreator = new DelayedKafkaUniversalProcessConfigCreator {
    override protected def schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  before {
    SinkForLongs.clear()
  }

  test("properly process data using kafka-generic-delayed source") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-long-field", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'field'", "0L")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("timestampField and delay param are null") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-null-params", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "null")
    runAndVerify(topicConfig, process, LongFieldV1.record)
  }

  test("handle not exist timestamp field param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-unknown-field", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "'unknownField'", "null")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include ("Field: 'unknownField' doesn't exist in definition: field.")
  }

  test("handle invalid negative param") {
    val topicConfig = createAndRegisterTopicConfig("simple-topic-with-negative-delay", LongFieldV1.schema)
    val process = createProcessWithDelayedSource(topicConfig.input, ExistingSchemaVersion(1), "null", "-10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topicConfig, process, LongFieldV1.record)
    }.getMessage should include ("LowerThanRequiredParameter(This field value has to be a number greater than or equal to 0,Please fill field with proper number,delayInMillis,start)")
  }

  private def runAndVerify(topicConfig: TopicConfig, process: CanonicalProcess, givenObj: AnyRef): Unit = {
    kafkaClient.createTopic(topicConfig.input, partitions = 1)
    pushMessage(givenObj, topicConfig.input)
    run(process) {
      eventually {
        RecordingExceptionConsumer.dataFor(runId) shouldBe empty
        SinkForLongs.data should have size 1
      }
    }
  }

  private def createProcessWithDelayedSource(topic: String, version: SchemaVersionOption, timestampField: String,  delay: String) = {

    import spel.Implicits._

    ScenarioBuilder.streaming("kafka-universal-delayed-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-universal-delayed",
        s"$TopicParamName" -> s"'${topic}'",
        s"$SchemaVersionParamName" -> asSpelExpression(formatVersionParam(version)),
        s"$TimestampFieldParamName" -> s"${timestampField}",
        s"$DelayParameterName" -> s"${delay}"
      )
      .emptySink("out", "sinkForLongs", SinkValueParamName -> "T(java.time.Instant).now().toEpochMilli()")
  }

}

class DelayedKafkaUniversalProcessConfigCreator extends KafkaAvroTestProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-universal-delayed" -> defaultCategory(new DelayedUniversalKafkaSourceFactory[String, GenericRecord](schemaRegistryClientFactory, createSchemaBasedMessagesSerdeProvider,
        processObjectDependencies, new FlinkKafkaDelayedSourceImplFactory(None, GenericRecordTimestampFieldAssigner(_))))
    )
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForLongs" -> defaultCategory(SinkForLongs.toSinkFactory)
    )
  }

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    super.expressionConfig(processObjectDependencies).copy(additionalClasses = List(classOf[Instant]))
  }
}
