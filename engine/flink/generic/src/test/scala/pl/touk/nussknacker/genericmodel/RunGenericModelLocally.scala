package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ProcessManagerProvider
import pl.touk.nussknacker.engine.testing.{LocalModelData, ProcessManagerProviderStub}
import pl.touk.nussknacker.ui.util.LocalNussknackerWithSingleModel

//Sample app to simplify local development.
object RunGenericModelLocally extends App {

  val modelConfig = ConfigFactory.empty()
  val modelData = LocalModelData(modelConfig, new GenericConfigCreator)

  val managerConfig = ConfigFactory.empty()
  //For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: ProcessManagerProvider = new ProcessManagerProviderStub
  LocalNussknackerWithSingleModel.run(modelData, provider, managerConfig, Set("Default"))

}
