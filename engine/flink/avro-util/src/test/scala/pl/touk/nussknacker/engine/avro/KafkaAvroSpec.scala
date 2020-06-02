package pl.touk.nussknacker.engine.avro

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.test.NussknackerAssertions

trait KafkaAvroSpec extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers with LazyLogging with NussknackerAssertions {

  import collection.JavaConverters._

  protected def schemaRegistryClient: CSchemaRegistryClient

  // schema.registry.url have to be defined even for MockSchemaRegistryClient
  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  protected lazy val processObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, DefaultObjectNaming)

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  protected lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  protected lazy val nodeId: NodeId = NodeId("mock-node-id")

  protected lazy val keySerializer: KafkaAvroSerializer = {
    val serializer = new KafkaAvroSerializer(schemaRegistryClient)
    serializer.configure(Map[String, AnyRef]("schema.registry.url" -> "not_used").asJava, true)
    serializer
  }

  protected lazy val valueDeserializer: KafkaAvroDeserializer = new KafkaAvroDeserializer(schemaRegistryClient)
  protected lazy val valueSerializer: KafkaAvroSerializer = new KafkaAvroSerializer(schemaRegistryClient)
}
