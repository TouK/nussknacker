package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.DeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.{DeploymentManagerProviderStub, LocalModelData}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel

//Sample app to simplify local development.
object RunGenericModelLocally extends App {

  val modelConfig = ConfigFactory.empty()
    .withValue("KAFKA_ADDRESS", fromAnyRef("localhost:3032"))
    .withValue("SCHEMA_REGISTRY_URL", fromAnyRef("http://localhost:3082"))
  val modelData = LocalModelData(modelConfig, new GenericConfigCreator)

  val managerConfig = ConfigFactory.empty()
  //For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: DeploymentManagerProvider = new DeploymentManagerProviderStub
  LocalNussknackerWithSingleModel.run(modelData, provider, managerConfig, Set("Default"))

}
