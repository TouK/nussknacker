package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.scalatest.BeforeAndAfter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schema.{PaymentV1, TestSchemaWithRecord}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.graph.expression
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

object KafkaAvroSinkFactoryV2IntegrationTest {
  import spel.Implicits._

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
         |    {
         |      "name": "amount",
         |      "type": "double"
         |    }
         |   ]
         |}
    """.stripMargin

    val toSampleParams: List[(String, expression.Expression)] = List(
      "id" -> "'record1'",
      "amount" -> "20.0")

    override def exampleData: Map[String, Any] = Map(
      "id" -> "record1",
      "amount" -> 20.0)
  }
}

class KafkaAvroSinkFactoryV2IntegrationTest extends KafkaAvroSpecMixin with BeforeAndAfter {
  import KafkaAvroSinkFactoryV2IntegrationTest._
  import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
  import spel.Implicits._

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

  test("should read event in the same version as source requires and save it in the same version") {
    val topicConfig = createAndRegisterTopicConfig("simple", MyRecord.schema)
    val sourceParam = SourceAvroParam.forGeneric(topicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(topic = topicConfig.output, versionOption = ExistingSchemaVersion(1),
      valueEither = Left(MyRecord.toSampleParams), key = "", ValidationMode.strict, sinkId = "kafka-avro-v2")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, event = MyRecord.record, expected = MyRecord.record)
  }
}
