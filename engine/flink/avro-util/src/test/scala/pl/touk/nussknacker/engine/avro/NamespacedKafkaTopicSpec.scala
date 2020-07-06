package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext, ObjectNaming, ObjectNamingParameters}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schema.PaymentV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.DefaultCache

class NamespacedKafkaSourceSinkTest extends KafkaAvroSpecMixin {

  private val namespace: String = "touk"

  private val mockSchemaRegistry = new KafkaAvroNamespacedMockSchemaRegistry(namespace)

  protected val objectNaming: TestObjectNaming = new TestObjectNaming(namespace)

  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
    .withValue("namespace", fromAnyRef(namespace))

  override protected lazy val processObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, objectNaming)

  override def schemaRegistryClient: MockSchemaRegistryClient = mockSchemaRegistry.schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = mockSchemaRegistry.factory

  test("should create source with proper filtered and converted topics") {
    val sourceFactory = createAvroSourceFactory(useSpecificAvroReader = false)
    val editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue(s"'payment'", "payment"), FixedExpressionValue(s"'payment_2'", "payment_2"))))

    sourceFactory.initialParameters.find(_.name == "topic").head.editor shouldBe editor
  }

  test("should create sink with proper filtered and converted topics") {
    val sinkFactory = createAvroSinkFactory(useSpecificAvroReader = false)
    val editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue(s"'payment'", "payment"), FixedExpressionValue(s"'payment_2'", "payment_2"))))

    sinkFactory.initialParameters.find(_.name == "topic").head.editor shouldBe editor
  }
}

class TestObjectNaming(namespace: String) extends ObjectNaming {

  private val namespacePattern = s"${namespace}_(.*)".r

  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String = namingContext.usageKey match {
    case KafkaUsageKey => s"${namespace}_$originalName"
    case _ => originalName
  }

  override def decodeName(preparedName: String, config: Config, namingContext: NamingContext): Option[String] =
    (namingContext.usageKey, preparedName) match {
      case (KafkaUsageKey, namespacePattern(value)) => Some(value)
      case _ => Option.empty
    }

  override def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters] = None
}

class KafkaAvroNamespacedMockSchemaRegistry(val namespace: String) {

  final val TestTopic: String = "test_topic"
  final val SomeTopic: String = "topic"
  final val PaymentWithNamespaced: String = s"${namespace}_payment"
  final val PaymentWithNamespaced2: String = s"${namespace}_payment_2"

  final val namespaceTopics = List(PaymentWithNamespaced, PaymentWithNamespaced2)

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
      .register(PaymentWithNamespaced, PaymentV1.schema, 1, isKey = false)
      .register(PaymentWithNamespaced2, PaymentV1.schema, 1, isKey = false)
      .build

  /**
    * It has to be done in this way, because schemaRegistryMockClient is not serializable..
    * And when we use TestSchemaRegistryClientFactory then flink has problem with serialization this..
    */
  val factory: CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory(DefaultCache.defaultMaximumSize, None, None, None) {
      override protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
        schemaRegistryMockClient
    }
}
