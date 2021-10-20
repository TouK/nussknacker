package pl.touk.nussknacker.engine.avro

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.avro.Schema
import org.scalatest.OptionValues
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext, ObjectNaming, ObjectNamingParameters}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.PaymentV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.util.KeyedValue
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData

class NamespacedKafkaSourceSinkTest extends KafkaAvroSpecMixin with OptionValues {

  import KafkaAvroNamespacedMockSchemaRegistry._

  protected val objectNaming: ObjectNaming = new TestObjectNaming(namespace)

  override protected def prepareConfig: Config = {
    super.prepareConfig
      .withValue("namespace", fromAnyRef(namespace))
  }

  override protected lazy val testProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, objectNaming)

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory =
    new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider: SchemaRegistryProvider =
      ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator, objectNaming = objectNaming)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), executionConfigPreparerChain(modelData))
  }

  test("should read event in the same version as source requires and save it in the same version") {
    val topicConfig = TopicConfig(InputPaymentWithNamespaced, OutputPaymentWithNamespaced, PaymentV1.schema, isKey = false)
    // Process should be created from topic without namespace..
    val processTopicConfig = TopicConfig("input_payment", "output_payment", PaymentV1.schema, isKey = false)
    val sourceParam = SourceAvroParam.forGeneric(processTopicConfig, ExistingSchemaVersion(1))
    val sinkParam = SinkAvroParam(processTopicConfig, ExistingSchemaVersion(1), "#input")
    val process = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, PaymentV1.record, PaymentV1.record)
  }
}

class TestObjectNaming(namespace: String) extends ObjectNaming {

  private final val NamespacePattern = s"${namespace}_(.*)".r

  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String = namingContext.usageKey match {
    case KafkaUsageKey => s"${namespace}_$originalName"
    case _ => originalName
  }

  override def decodeName(preparedName: String, config: Config, namingContext: NamingContext): Option[String] =
    (namingContext.usageKey, preparedName) match {
      case (KafkaUsageKey, NamespacePattern(value)) => Some(value)
      case _ => Option.empty
    }

  override def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters] = None
}

object KafkaAvroNamespacedMockSchemaRegistry {

  final val namespace: String = "touk"

  final val TestTopic: String = "test_topic"
  final val SomeTopic: String = "topic"
  final val InputPaymentWithNamespaced: String = s"${namespace}_input_payment"
  final val OutputPaymentWithNamespaced: String = s"${namespace}_output_payment"

  private val IntSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val schemaRegistryMockClient: MockSchemaRegistryClient =
    new MockConfluentSchemaRegistryClientBuilder()
      .register(TestTopic, IntSchema, 1, isKey = true) // key subject should be ignored
      .register(TestTopic, PaymentV1.schema, 1, isKey = false) // topic with bad namespace should be ignored
      .register(SomeTopic, PaymentV1.schema, 1, isKey = false) // topic without namespace should be ignored
      .register(InputPaymentWithNamespaced, PaymentV1.schema, 1, isKey = false)
      .register(OutputPaymentWithNamespaced, PaymentV1.schema, 1, isKey = false)
      .build

}
