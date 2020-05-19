package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.test.NussknackerAssertions

abstract class KafkaAvroSpec(topics: List[(String, Int)])
  extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions {

  import collection.JavaConverters._

  def schemaRegistryClient: ConfluentSchemaRegistryClient

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  lazy val processObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  protected lazy val keySerializer: KafkaAvroSerializer = {
    val serializer = new KafkaAvroSerializer(schemaRegistryClient.client)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  protected lazy val valueSerializer = new KafkaAvroSerializer(schemaRegistryClient.client)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    topics.foreach(data => kafkaClient.createTopic(data._1, data._2))
  }
}

trait TestMockSchemaRegistry {

  def createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient): ConfluentSchemaRegistryClientFactory =
    new ConfluentSchemaRegistryClientFactory with Serializable {
      override def createSchemaRegistryClient(kafkaConfig: KafkaConfig): ConfluentSchemaRegistryClient =
        confluentSchemaRegistryMockClient
    }

  def topics: List[(String, Int)]
}
