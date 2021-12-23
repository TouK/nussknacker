package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.DeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.{DeploymentManagerProviderStub, LocalModelData}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel

//Sample app to simplify local development.
object RunDefaultModelLocally extends App {

  val modelConfig = ConfigFactory.empty()
    // TODO: Fix: Idea loads kafka lite component provider
    .withValue("kafka.kafkaAddress", fromAnyRef("notused:1111"))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("notused:1111"))
  val modelData = LocalModelData(modelConfig, new DefaultConfigCreator)

  val managerConfig = ConfigFactory.empty()
  //For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: DeploymentManagerProvider = new DeploymentManagerProviderStub
  LocalNussknackerWithSingleModel.run(modelData, provider, managerConfig, Set("Default"))

}
