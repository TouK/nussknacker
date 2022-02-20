package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config

trait ObjectNaming extends Serializable {
  def prepareName(originalName: String, config: Config, namingContext: NamingContext): String
  def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters]
  def decodeName(preparedName: String, config: Config, namingContext: NamingContext): Option[String]
}

trait ObjectNamingParameters {
  /**
   * This function is used in [[pl.touk.nussknacker.engine.process.runner.FlinkProcessMain FlinkProcessMain]] to pass
   * to the [[pl.touk.nussknacker.engine.flink.api.NkGlobalParameters NkGlobalParameters]] tags that are to be used when
   * producing metrics in [[pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario]]. It may be changed in the future.
   */
  def toTags: Map[String, String]
}