package pl.touk.nussknacker.engine.schemedkafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.avro.Schema
import org.scalatest.OptionValues
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroNamespacedSpec.sinkForInputMetaResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.PaymentV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{
  MockConfluentSchemaRegistryClientBuilder,
  MockSchemaRegistryClient
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.testing.LocalModelData

class KafkaAvroNamespacedSpec extends KafkaAvroSpecMixin with OptionValues {

  import KafkaAvroNamespacedMockSchemaRegistry._

  override protected def resolveConfig(config: Config): Config = {
    super
      .resolveConfig(config)
      .withValue("namespace", fromAnyRef(namespace))
  }

  override protected lazy val testModelDependencies: ProcessObjectDependencies =
    ProcessObjectDependencies.withConfig(config)

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  private lazy val creator: KafkaAvroTestProcessConfigCreator =
    new KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {
      override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
        MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    modelData = LocalModelData(config, List.empty, configCreator = creator)
  }

  test("should read event in the same version as source requires and save it in the same version") {
    val topicConfig =
      TopicConfig(InputPaymentWithNamespaced, OutputPaymentWithNamespaced, PaymentV1.schema, isKey = false)
    // Process should be created from topic without namespace..
    val processTopicConfig = TopicConfig(
      input = TopicName.ForSource("input_payment"),
      output = TopicName.ForSink("output_payment"),
      schema = PaymentV1.schema,
      isKey = false
    )
    val sourceParam = SourceAvroParam.forUniversal(processTopicConfig, ExistingSchemaVersion(1))
    val sinkParam   = UniversalSinkParam(processTopicConfig, ExistingSchemaVersion(1), "#input")
    val process     = createAvroProcess(sourceParam, sinkParam)

    runAndVerifyResult(process, topicConfig, List(PaymentV1.record), PaymentV1.record)
  }

}

object KafkaAvroNamespacedSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}

object KafkaAvroNamespacedMockSchemaRegistry {

  final val namespace: String = "touk"

  final val TestTopic: String           = "test_topic"
  final val SomeTopic: String           = "topic"
  final val InputPaymentWithNamespaced  = TopicName.ForSource(s"${namespace}_input_payment")
  final val OutputPaymentWithNamespaced = TopicName.ForSink(s"${namespace}_output_payment")

  private val IntSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "int"
      |}
    """.stripMargin
  )

  val schemaRegistryMockClient: MockSchemaRegistryClient =
    new MockConfluentSchemaRegistryClientBuilder()
      .register(TestTopic, IntSchema, 1, isKey = true)         // key subject should be ignored
      .register(TestTopic, PaymentV1.schema, 1, isKey = false) // topic with bad namespace should be ignored
      .register(SomeTopic, PaymentV1.schema, 1, isKey = false) // topic without namespace should be ignored
      .register(InputPaymentWithNamespaced.name, PaymentV1.schema, 1, isKey = false)
      .register(OutputPaymentWithNamespaced.name, PaymentV1.schema, 1, isKey = false)
      .build

}
