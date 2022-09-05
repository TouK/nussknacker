package pl.touk.nussknacker.engine.schemedkafka.helpers

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.WithConfig

trait SchemaRegistryMixin extends AnyFunSuite with KafkaSpec with KafkaWithSchemaRegistryOperations with WithConfig {

  override protected def resolveConfig(config: Config): Config = {
    super.resolveConfig(config)
      .withValue("kafka.kafkaProperties.\"bootstrap.servers\"", fromAnyRef(kafkaServer.kafkaAddress))
      // schema.registry.url have to be defined even for MockSchemaRegistryClient
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))
      .withValue("kafka.avroKryoGenericRecordSchemaIdSerialization", fromAnyRef(true))
      // we turn off auto registration to do it on our own passing mocked schema registry client // meaningful only in Flink tests
      .withValue(s"kafka.kafkaEspProperties.autoRegisterRecordSchemaIdSerialization", fromAnyRef(false))
  }

  protected lazy val testProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))

  protected lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)

}
