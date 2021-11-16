package pl.touk.nussknacker.engine.avro.helpers

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

trait SchemaRegistryMixin extends FunSuite with KafkaSpec with KafkaWithSchemaRegistryOperations {

  override lazy val config: Config = prepareConfig

  def prepareConfig: Config = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
      .withValue("kafka.avroKryoGenericRecordSchemaIdSerialization", fromAnyRef(true))
      // we turn off auto registration to do it on our own passing mocked schema registry client
      .withValue(s"kafka.kafkaEspProperties.${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty}", fromAnyRef(false))
  }

  protected lazy val testProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)

}
