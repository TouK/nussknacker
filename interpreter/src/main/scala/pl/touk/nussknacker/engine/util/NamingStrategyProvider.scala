package pl.touk.nussknacker.engine.util

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

object NamingStrategyProvider {
  private val NamespacePath = "namespace"

  def apply(modelConfig: Config): NamingStrategy = {
    val namespaceOpt = if (modelConfig.hasPath(NamespacePath)) {
      Some(modelConfig.getString(NamespacePath))
    } else {
      None
    }
    NamingStrategy(namespaceOpt)
  }

}
