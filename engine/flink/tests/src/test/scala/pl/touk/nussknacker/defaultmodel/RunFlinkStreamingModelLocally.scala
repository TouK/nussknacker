package pl.touk.nussknacker.defaultmodel

import cats.effect.{IO, IOApp}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.DeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.{DeploymentManagerProviderStub, LocalModelData}
import pl.touk.nussknacker.test.KafkaConfigProperties
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel

//Sample app to simplify local development.
object RunFlinkStreamingModelLocally extends IOApp.Simple {

  val modelConfig = ConfigFactory
    .empty()
    // TODO: Fix: Idea loads kafka lite component provider
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("kafka_should_not_be_used:9092"))
    .withValue(
      KafkaConfigProperties.property("schema.registry.url"),
      fromAnyRef("schema_registry_should_not_be_used:8081")
    )

  val modelData = LocalModelData(modelConfig, List.empty, configCreator = new DefaultConfigCreator)

  val managerConfig = ConfigFactory.empty()
  // For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: DeploymentManagerProvider = new DeploymentManagerProviderStub

  override def run: IO[Unit] = {
    LocalNussknackerWithSingleModel
      .run(modelData, provider, managerConfig)
      .use(_ => IO.never)
  }

}
