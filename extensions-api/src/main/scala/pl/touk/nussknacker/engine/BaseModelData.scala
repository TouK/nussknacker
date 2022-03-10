package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import java.net.URL

// TODO: Replace ModelData -> BasedModelData inheritance with composition. Thanks to that it won't be needed to downcast
//       to ModelData in case of interpreter invocation and designer -> interpreter dependency will disappear
trait BaseModelData {

  def objectNaming: ObjectNaming

  def inputConfigDuringExecution: InputConfigDuringExecution

  def processConfig: Config

  def modelClassLoaderUrls: List[URL]

}
