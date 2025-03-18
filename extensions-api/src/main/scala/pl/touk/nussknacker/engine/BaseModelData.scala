package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import java.net.URLClassLoader

// TODO: Replace ModelData -> BasedModelData inheritance with composition. Thanks to that it won't be needed to downcast
//       to ModelData in case of interpreter invocation
trait BaseModelData {

  // TODO: We should consider loading all model plugins that we don't need to reload at application start time -
  //       in the same way how we did with modelClassLoader. Thanks to that there won't be need to think
  //       if getCurrentModelData() is what is needed in given time and we could initialize some variables once instead
  //       for each request
  def namingStrategy: NamingStrategy

  def inputConfigDuringExecution: InputConfigDuringExecution

  def modelConfig: Config

}

trait BaseModelDataProvider {
  // modelClassLoader is set once and won't be changed
  val modelClassLoader: URLClassLoader

  // other model data could be reloaded anytime so we need to always consider when this method should be invoke
  def getCurrentModelData(): BaseModelData
}
