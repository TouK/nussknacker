package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import com.typesafe.config.Config
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.ExecutionConfig
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ScenarioCompilationErrors
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName._
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, TestFlinkKafkaComponentProvider}
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder
import pl.touk.nussknacker.engine.schemedkafka.helpers.FlinkKafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.{AvroEnum, TestSchema, TestSchemaWithRecord}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.jdk.CollectionConverters._

class SinkValueEditorWithAvroPayloadIntegrationTest
    extends AnyFunSuite
    with FlinkKafkaAvroSpecMixin
    with BeforeAndAfter {

  import SinkValueEditorWithAvroPayloadIntegrationTest._

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val components = new TestFlinkKafkaComponentProvider(schemaRegistryClientFactory, sinkForInputMetaResultsHolder)
      .createComponents(ModelConfig.parse(modelConfig))

    testScenarioRunner = TestScenarioRunner
      .flinkBased(modelConfig, flinkMiniCluster)
      .withExtraComponents(components)
      .withExtraSerializersRegistrars(List((_: Config, executionConfig: ExecutionConfig) => {
        new AvroSerializersRegistrar().registerOptimizedSerializer(
          executionConfig.getSerializerConfig,
          schemaRegistryClientFactory,
          List(kafkaComponentsConfig)
        )
      }))
      .build()
  }

  test("should pass through record") {
    val schema      = MyRecord.schema
    val topicConfig = createAndRegisterTopicConfig("record", schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam.form(
      topic = topicConfig.output,
      version = ExistingSchemaVersion(1),
      valueParams = MyRecord.toSampleParams,
    )
    val scenario = createAvroProcess(sourceParam, sinkParam)
    runAndVerifyResultSingleEvent(scenario, topicConfig, event = MyRecord.record, expected = MyRecord.record)
  }

  test("should pass through primitive at top level") {
    val schema      = AvroUtils.parseSchema("""{"type": "long"}""")
    val topicConfig = createAndRegisterTopicConfig("long", schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam.nonRecordForm(topicConfig.output, ExistingSchemaVersion(1), "42L".spel)
    val scenario    = createAvroProcess(sourceParam, sinkParam)
    val encoded     = encode(42L, schema)
    runAndVerifyResultSingleEvent(scenario, topicConfig, event = encoded, expected = java.lang.Long.valueOf(42L))
  }

  test("should pass through array at top level") {
    val schema      = AvroUtils.parseSchema("""{"type": "array", "items": "long"}""")
    val topicConfig = createAndRegisterTopicConfig("array", schema)
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam.nonRecordForm(topicConfig.output, ExistingSchemaVersion(1), "{42L}".spel)
    val process     = createAvroProcess(sourceParam, sinkParam)
    val encoded     = encode(new NonRecordContainer(schema, List(42L).asJava), schema)
    runAndVerifyResultSingleEvent(process, topicConfig, event = encoded, expected = List(42L).asJava)
  }

  test("should pass through enum when input and output enum schemas are identical") {
    val (inputTopic, outputTopic) = topicNames("enum.schema.identical")
    val scenario = createScenarioWithEnumSink(
      inputTopic = inputTopic,
      inputTopicSchema = AvroEnum.V1.schema,
      outputTopic = outputTopic,
      testFieldValue = "#input.testField".spel
    )

    val input = AvroEnum.V1.record
    pushMessage(input, inputTopic)

    runScenarioAndVerifyMessage(scenario, outputTopic, input)
  }

  test("should accept literal enum value when it matches output enum symbols") {
    val (inputTopic, outputTopic) = topicNames("enum.literal.valid")
    val scenario = createScenarioWithEnumSink(
      inputTopic = inputTopic,
      inputTopicSchema = AvroEnum.V1.schema,
      outputTopic = outputTopic,
      testFieldValue = "'BLUE'".spel
    )

    pushMessage(AvroEnum.V1.encode(Map("testField" -> "RED")), inputTopic)

    runScenarioAndVerifyMessage(scenario, outputTopic, AvroEnum.V1.encode(Map("testField" -> "BLUE")))
  }

  test("should accept enum value when input and output schemas differ but symbols overlap") {
    val inputSchema = new TestSchema {
      override def stringSchema: String =
        s"""{
           |  "type": "enum",
           |  "name": "OtherColor",
           |  "symbols": [
           |     "BLACK",
           |     "RED",
           |     "WHITE"
           |  ]
           |}
           |""".stripMargin
    }

    val (inputTopic, outputTopic) = topicNames("enum.schema.different.symbols.overlap")
    val scenario = createScenarioWithEnumSink(inputTopic, inputSchema.schema, outputTopic, "#input".spel)

    val input = "RED"
    pushMessage(encode(input, inputSchema.schema), inputTopic)

    val expectedEvent = AvroEnum.V1.encode(Map("testField" -> input))

    runScenarioAndVerifyMessage(scenario, outputTopic, expectedEvent)
  }

  test("should fail compilation when literal value is not a valid output enum symbol") {
    val (inputTopic, outputTopic) = topicNames("enum.literal.invalid")
    val scenario = createScenarioWithEnumSink(inputTopic, AvroEnum.V1.schema, outputTopic, "'BLACK'".spel)

    val ex = intercept[ScenarioCompilationErrors] {
      runScenarioAndVerifyMessage(scenario, outputTopic, AvroEnum.V1.record)
    }

    ex.errors shouldBe List(
      CustomNodeError(
        NodeId("end"),
        "Errors:\nIncorrect value: actual value: 'BLACK' should be one of: ['RED', 'GREEN', 'BLUE'].",
        Some(ParameterName("testField"))
      )
    )
  }

  test("should fail at runtime when input enum symbol is incompatible with output enum") {
    val (inputTopic, outputTopic) = topicNames("enum.incompatible.runtime")
    val scenario =
      createScenarioWithEnumSink(inputTopic, AvroEnum.V2.schema, outputTopic, "#input.testField".spel)

    val input = Map("testField" -> null)
    pushMessage(AvroEnum.V2.encode(input), inputTopic)

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      eventually {
        assertAvroSinkRuntimeExceptionContains("Not expected null for field: testField")
      }
    }
  }

  test("should fail at runtime when input string is not a valid output enum symbol") {
    val inputSchema = new TestSchema {
      override def stringSchema: String =
        s"""{
           |  "type": "string",
           |  "name": "input"
           |}
           |""".stripMargin
    }

    val (inputTopic, outputTopic) = topicNames("enum.from-string.invalid.runtime")
    val scenario = createScenarioWithEnumSink(inputTopic, inputSchema.schema, outputTopic, "#input".spel)

    val input = "BLACK"
    pushMessage(encode(input, inputSchema.schema), inputTopic)

    testScenarioRunner.withRunningScenario(scenario) { s =>
      eventually {
        assertAvroSinkRuntimeExceptionContains(
          s"Not expected symbol: $input for field: testField with schema: com.example.Color, allowed values: RED, GREEN, BLUE"
        )
      }
    }
  }

  private def createScenarioWithEnumSink(
      inputTopic: TopicName.ForSource,
      inputTopicSchema: Schema,
      outputTopic: TopicName.ForSink,
      testFieldValue: Expression
  ) = {
    registerSchema(inputTopic.toUnspecialized, inputTopicSchema, isKey = false)
    registerSchema(outputTopic.toUnspecialized, AvroEnum.V1.schema, isKey = false)

    kafkaClient.createTopic(inputTopic.name, partitions = 1)
    kafkaClient.createTopic(outputTopic.name, partitions = 1)

    val sourceParam = UniversalSourceParam(inputTopic.name, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam.form(
      topic = outputTopic,
      version = ExistingSchemaVersion(1),
      valueParams = List("testField" -> testFieldValue),
    )
    createAvroProcess(sourceParam, sinkParam)
  }

  private def runScenarioAndVerifyMessage(
      scenario: CanonicalProcess,
      outputTopic: TopicName.ForSink,
      expectedEvent: GenericRecord
  ): Unit = {
    testScenarioRunner.withRunningScenario(scenario) { _ =>
      consumeAndVerifyMessages(outputTopic, List(expectedEvent))
    }
  }

  private def assertAvroSinkRuntimeExceptionContains(message: String) = {
    RecordingExceptionConsumer.exceptionsFor(runId) should have size 1
    val nuExceptionInfo = RecordingExceptionConsumer.exceptionsFor(runId).head

    nuExceptionInfo.nodeComponentInfo shouldBe Some(
      NodeComponentInfo(NodeId("end"), NodeName("end"), ComponentType.Sink, "flinkKafkaUniversalSink")
    )
    nuExceptionInfo.throwable shouldBe a[AvroRuntimeException]
    nuExceptionInfo.throwable.getMessage should include(message)
  }

  private def topicNames(name: String): (TopicName.ForSource, TopicName.ForSink) = {
    val prefix     = "test.avro"
    val inputName  = TopicName.ForSource(s"$prefix.input.$name")
    val outputName = TopicName.ForSink(s"$prefix.output.$name")
    (inputName, outputName)
  }

}

object SinkValueEditorWithAvroPayloadIntegrationTest {

  private val sinkForInputMetaResultsHolder         = new TestResultsHolder[java.util.Map[String @unchecked, _]]
  private val avroEncoder: ToAvroSchemaBasedEncoder = ToAvroSchemaBasedEncoder(ValidationMode.strict)

  private def encode(a: Any, schema: Schema): AnyRef =
    avroEncoder
      .encode(a, schema)
      .valueOr(es => throw new AvroRuntimeException(es.toList.mkString(",")))

  private object MyRecord extends TestSchemaWithRecord {

    override val stringSchema: String =
      s"""
         |{
         |  "type": "record",
         |  "name": "MyRecord",
         |  "fields": [
         |    {
         |      "name": "id",
         |      "type": "string"
         |    },
         |    { "name": "arr", "type": { "type": "array", "items": "long" } },
         |    {
         |      "name": "amount",
         |      "type": ["double", "string"]
         |    },
         |    {
         |      "name": "nested",
         |      "type": {
         |        "type": "record",
         |        "name": "nested",
         |        "fields": [
         |          {
         |            "name": "id",
         |            "type": "string"
         |          }
         |        ]
         |      }
         |    }
         |   ]
         |}
    """.stripMargin

    val toSampleParams: List[(String, expression.Expression)] = List(
      "id"        -> "'record1'".spel,
      "amount"    -> "20.0".spel,
      "arr"       -> "{1L}".spel,
      "nested.id" -> "'nested_record1'".spel
    )

    override def exampleData: Map[String, Any] = Map(
      "id"     -> "record1",
      "amount" -> 20.0,
      "arr"    -> List(1L),
      "nested" -> Map(
        "id" -> "nested_record1"
      )
    )

  }

}
