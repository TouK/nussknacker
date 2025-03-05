package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import java.net.URL
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

// TODO: Replace ModelData -> BasedModelData inheritance with composition. Thanks to that it won't be needed to downcast
//       to ModelData in case of interpreter invocation
trait BaseModelData {

  def namingStrategy: NamingStrategy

  def inputConfigDuringExecution: InputConfigDuringExecution

  // Deprecated, use modelConfig instead
  final def processConfig: Config = modelConfig

  def modelConfig: Config

  def modelClassLoader: URLClassLoader

  final def modelClassLoaderUrls: List[URL] = modelClassLoader.getURLs.toList

}
