package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ProcessManagerProvider
import pl.touk.nussknacker.engine.testing.{LocalModelData, ProcessManagerProviderStub}

//Sample app to simplify local development.
object RunReturningModelLocally extends App {

  val modelConfig = ConfigFactory.empty()
  val modelData = LocalModelData(modelConfig, new DevProcessConfigCreator)

  val managerConfig = ConfigFactory.empty()
  //For simplicity we use stub here, one can add real Flink implementation after add appropriate dependencies
  val provider: ProcessManagerProvider = new ProcessManagerProviderStub
  LocalNussknackerWithSingleModel.run(modelData, provider, managerConfig, Set("Default"))

}
