package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.{AvroRuntimeException, Schema}
import org.scalatest.BeforeAndAfter
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.TestSchemaWithRecord
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, KafkaAvroTestProcessConfigCreator}
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression
import pl.touk.nussknacker.engine.testing.LocalModelData

private object SinkValueEditorWithAvroPayloadIntegrationTest {

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
      "id" -> "'record1'",
      "amount" -> "20.0",
      "arr" -> "{1L}",
      "nested.id" -> "'nested_record1'"
    )

    override def exampleData: Map[String, Any] = Map(
      "id" -> "record1",
      "amount" -> 20.0,
      "arr" -> List(1L),
      "nested" -> Map(
        "id" -> "nested_record1"
      )
    )
  }

  val topicSchemas = Map(
    "record" -> MyRecord.schema,
    "long" -> AvroUtils.parseSchema("""{"type": "long"}"""),
    "array" -> AvroUtils.parseSchema("""{"type": "array", "items": "long"}""")
  )
}

class SinkValueEditorWithAvroPayloadIntegrationTest extends KafkaAvroSpecMixin with BeforeAndAfter {
  import SinkValueEditorWithAvroPayloadIntegrationTest._

  private var topicConfigs: Map[String, TopicConfig] = Map.empty

  private lazy val processConfigCreator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
  }

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, processConfigCreator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
    topicSchemas.foreach { case (topicName, schema) =>
      topicConfigs = topicConfigs + (topicName -> createAndRegisterTopicConfig(topicName, schema))
    }
  }

  test("record") {
    val topicConfig = topicConfigs("record")
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam(topic = topicConfig.output, versionOption = ExistingSchemaVersion(1),
      valueParams = MyRecord.toSampleParams, key = "", validationMode = None)
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, event = MyRecord.record, expected = MyRecord.record)
  }

  test("primitive at top level") {
    val topicConfig = topicConfigs("long")
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "42L", validationMode = None)
    val process = createAvroProcess(sourceParam, sinkParam)
    val encoded = encode(42L, topicSchemas("long"))
    runAndVerifyResult(process, topicConfig, event = encoded, expected = encoded)
  }

  test("array at top level") {
    val topicConfig = topicConfigs("array")
    val sourceParam = SourceAvroParam.forUniversal(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = UniversalSinkParam(topicConfig, ExistingSchemaVersion(1), "{42L}")
    val process = createAvroProcess(sourceParam, sinkParam)
    val thrown = intercept[IllegalArgumentException] {
      runAndVerifyResult(process, topicConfig, event = null, expected = null)
    }
    thrown.getMessage shouldBe "Compilation errors: CustomNodeError(end,Unsupported Avro type. Top level Arrays are not supported,None)"
  }
}
