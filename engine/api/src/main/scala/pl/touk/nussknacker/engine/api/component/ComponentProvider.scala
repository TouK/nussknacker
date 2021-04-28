package pl.touk.nussknacker.engine.api.component

import com.typesafe.config.{Config, ConfigFactory}
import com.vdurmont.semver4j.Semver
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

/*
  Service, SourceFactory, SinkFactory, CustomStreamTransformer
 */
trait Component

case class ComponentProviderConfig( //if not present, we assume providerType is equal to component name
                                    providerType: Option[String],
                                    disabled: Boolean = false,
                                    //TODO: more configurable/extensible way of name customization
                                    componentPrefix: Option[String],
                                    categories: List[String] = Nil,
                                    config: Config = ConfigFactory.empty())

/**
  * Implementations should be registered with ServiceLoader mechanism. Each provider can be configured multiple times
  * (e.g. differnent DBs, different OpenAPI registrars and so on.
  */
trait ComponentProvider {

  def providerName: String

  //in some cases e.g. external model/service registry we don't want to resolve registry settings
  //on Flink (it can be in different network location, or have lower HA guarantees), @see ModelConfigLoader
  def resolveConfigForExecution(config: Config): Config

  def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition]

  def isCompatible(version: NussknackerVersion): Boolean

}

case class NussknackerVersion(value: Semver) extends AnyVal

case class ComponentDefinition(name: String, component: Component)







