package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.DeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.{DeploymentManagerProviderStub, LocalModelData}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel

//Sample app to simplify local development.
object RunGenericModelLocally extends App {

  val modelConfig = ConfigFactory.empty()
  val modelData = LocalModelData(modelConfig, new DefaultConfigCreator)

  val managerConfig = ConfigFactory.empty()
  //For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: DeploymentManagerProvider = new DeploymentManagerProviderStub
  LocalNussknackerWithSingleModel.run(modelData, provider, managerConfig, Set("Default"))

}
