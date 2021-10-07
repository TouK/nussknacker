package pl.touk.nussknacker.engine.api.component

import com.typesafe.config.{Config, ConfigFactory}
import com.vdurmont.semver4j.Semver
import net.ceedubs.ficus.readers.{ArbitraryTypeReader, ValueReader}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.version.BuildInfo

/*
  Service, SourceFactory, SinkFactory, CustomStreamTransformer
 */
trait Component

object ComponentProviderConfig {

  /*
    We use own reader, to insert additional config parameters to config field, so that ComponentProviderConfig has shape
    {
      providerType: "type1"
      value1: "abc"
      value2: "def"
    }
    instead of
    {
      providerType: "type1"
      config {
        value1: "abc"
        value2: "def"
      }
    }

   */
  implicit val reader: ValueReader[ComponentProviderConfig] = new ValueReader[ComponentProviderConfig] {
    import net.ceedubs.ficus.Ficus._
    private val normalReader = ArbitraryTypeReader.arbitraryTypeValueReader[ComponentProviderConfig]
    override def read(config: Config, path: String): ComponentProviderConfig = {
      normalReader.read(config, path).copy(config = config.getConfig(path))
    }
  }
}

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
  //on engine/executor side (e.g. on Flink it can be in different network location, or have lower HA guarantees), @see ModelConfigLoader
  def resolveConfigForExecution(config: Config): Config

  def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition]

  def isCompatible(version: NussknackerVersion): Boolean

}

object NussknackerVersion {

  val current: NussknackerVersion = NussknackerVersion(new Semver(BuildInfo.version))

}

case class NussknackerVersion(value: Semver)

case class ComponentDefinition(name: String, component: Component, icon: Option[String] = None, docsUrl: Option[String] = None)







