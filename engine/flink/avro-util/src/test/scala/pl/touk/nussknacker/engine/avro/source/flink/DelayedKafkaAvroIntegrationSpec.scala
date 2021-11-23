package pl.touk.nussknacker.engine.avro.source.flink

import org.apache.avro.generic.GenericRecord
import org.scalatest.{BeforeAndAfter, FunSuite}
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.LongFieldV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.source.delayed.DelayedKafkaAvroSourceFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory.{DelayParameterName, TimestampFieldParamName}
import pl.touk.nussknacker.engine.kafka.generic.FlinkKafkaDelayedSourceImplFactory
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.time.Instant

class DelayedKafkaAvroIntegrationSpec extends FunSuite with KafkaAvroSpecMixin with BeforeAndAfter  {

  private lazy val creator: ProcessConfigCreator = new DelayedAvroProcessConfigCreator {
    override protected def createSchemaRegistryProvider: SchemaRegistryProvider =
      ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
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

  after {
    recordingExceptionHandler.clear()
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

  private def runAndVerify(topicConfig: TopicConfig, process: EspProcess, givenObj: AnyRef): Unit = {
    kafkaClient.createTopic(topicConfig.input, partitions = 1)
    pushMessage(givenObj, topicConfig.input)
    run(process) {
      eventually {
        recordingExceptionHandler.data shouldBe empty
        SinkForLongs.data should have size 1
      }
    }
  }

  private def createProcessWithDelayedSource(topic: String, version: SchemaVersionOption, timestampField: String,  delay: String) = {

    import spel.Implicits._

    EspProcessBuilder.id("kafka-avro-delayed-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-avro-delayed",
        s"$TopicParamName" -> s"'${topic}'",
        s"$SchemaVersionParamName" -> asSpelExpression(formatVersionParam(version)),
        s"$TimestampFieldParamName" -> s"${timestampField}",
        s"$DelayParameterName" -> s"${delay}"
      )
      .emptySink("out", "sinkForLongs", "value" -> "T(java.time.Instant).now().toEpochMilli()")
  }

}

class DelayedAvroProcessConfigCreator extends KafkaAvroTestProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-avro-delayed" -> defaultCategory(new DelayedKafkaAvroSourceFactory[String, GenericRecord](schemaRegistryProvider, processObjectDependencies, new FlinkKafkaDelayedSourceImplFactory(None, GenericRecordTimestampFieldAssigner(_))))
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
