package pl.touk.nussknacker.engine.avro.sink

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.{AvroRuntimeException, Schema}
import org.scalatest.BeforeAndAfter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schema.TestSchemaWithRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.{AvroUtils, KafkaAvroSpecMixin, KafkaAvroTestProcessConfigCreator}
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression
import pl.touk.nussknacker.engine.testing.LocalModelData


private object KafkaAvroSinkFactoryWithEditorIntegrationTest {

  val avroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  def encode(a: Any, schema: Schema): AnyRef =
    avroEncoder.encode(a, schema)
      .valueOr(es => throw new AvroRuntimeException(es.toList.mkString(",")))

  object MyRecord extends TestSchemaWithRecord {

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
         |    {
         |      "name": "amount",
         |      "type": "double"
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
      "id" -> "'record1'",
      "amount" -> "20.0",
      "nested.id" -> "'nested_record1'"
    )

    override def exampleData: Map[String, Any] = Map(
      "id" -> "record1",
      "amount" -> 20.0,
      "nested" -> Map(
        "id" -> "nested_record1"
      )
    )
  }
}

class KafkaAvroSinkFactoryWithEditorIntegrationTest extends KafkaAvroSpecMixin with BeforeAndAfter {
  import KafkaAvroSinkFactoryWithEditorIntegrationTest._

  private lazy val processConfigCreator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider =
      ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient), processObjectDependencies)
  }

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, processConfigCreator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), config, executionConfigPreparerChain(modelData))
  }

  after {
    recordingExceptionHandler.clear()
  }

  test("record") {
    val topicConfig = createAndRegisterTopicConfig("record", MyRecord.schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topic = topicConfig.output, versionOption = ExistingSchemaVersion(1),
      valueParams = MyRecord.toSampleParams, key = "", ValidationMode.strict, sinkId = "kafka-avro-editor")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, event = MyRecord.record, expected = MyRecord.record)
  }

  test("long") {
    val schema = AvroUtils.parseSchema("""{"type": "long"}""")
    val topicConfig = createAndRegisterTopicConfig("long", schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "42L").copy(sinkId = "kafka-avro-editor")
    val process = createAvroProcess(sourceParam, sinkParam)
    val encoded = encode(42L, schema)
    runAndVerifyResult(process, topicConfig, event = encoded, expected = encoded)
  }

  test("array") {
    val schema = AvroUtils.parseSchema("""{"type": "array", "items": "long"}""")
    val topicConfig = createAndRegisterTopicConfig("array", schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topicConfig, ExistingSchemaVersion(1), "{42L}").copy(sinkId = "kafka-avro-editor")
    val process = createAvroProcess(sourceParam, sinkParam)
    val thrown = intercept[IllegalArgumentException] {
      runAndVerifyResult(process, topicConfig, event = null, expected = null)
    }
    thrown.getMessage shouldBe "Compilation errors: CustomNodeError(end,Unsupported Avro type. Supported types are null, Boolean, Integer, Long, Float, Double, String, byte[] and IndexedRecord,None)"
  }
}
