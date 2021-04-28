package pl.touk.nussknacker.engine.component

import com.typesafe.config.Config
import com.vdurmont.semver4j.Semver
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.component.ComponentExtractor.componentConfigPath
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.version.BuildInfo

object ComponentExtractor {

  val componentConfigPath = "components"

  def apply(classLoader: ClassLoader): ComponentExtractor = {
    val version = NussknackerVersion(new Semver(BuildInfo.version))
    ComponentExtractor(classLoader, version)
  }

}

case class ComponentExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private val providers = ScalaServiceLoader.load[ComponentProvider](classLoader).map(p => p.providerName -> p).toMap

  private def loadCorrectComponents(config: Config): Map[String, (ComponentProviderConfig, ComponentProvider)] = {
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val componentsConfig = config.getAs[Map[String, ComponentProviderConfig]](componentConfigPath).getOrElse(Map.empty)
    componentsConfig.filterNot(_._2.disabled).map {
      case (name, providerConfig: ComponentProviderConfig) =>
        val providerName = providerConfig.providerType.getOrElse(name)
        val provider = providers.getOrElse(providerName, throw new IllegalArgumentException(s"Provider $providerName not found"))
        if (!provider.isCompatible(nussknackerVersion)) {
          throw new IllegalArgumentException(s"Component provider $name (of type $providerName) is not compatible with $nussknackerVersion, please disable it explicitly ")
        }
        name -> (providerConfig, provider)
    }
  }

  def extract(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Component]] = reduceUnique {
    loadCorrectComponents(processObjectDependencies.config).map {
      case (_, (config, provider)) => extractOneProviderConfig(config, provider, processObjectDependencies)
    }
  }

  def loadAdditionalConfig(inputConfig: Config, configWithDefaults: Config): Config = {
    val resolvedConfigs = loadCorrectComponents(configWithDefaults).map {
      case (name, (config, provider)) => name -> provider.resolveConfigForExecution(config.config)
    }
    resolvedConfigs.foldLeft(inputConfig) {
      case (acc, (name, conf)) => acc.withValue(s"$componentConfigPath.$name.config", conf.root())
    }
  }

  private def extractOneProviderConfig(config: ComponentProviderConfig, provider: ComponentProvider, processObjectDependencies: ProcessObjectDependencies) = {
    val components = provider.create(config.config, processObjectDependencies).map { cd =>
      val finalName = config.componentPrefix.map(_ + cd.name).getOrElse(cd.name)
      finalName -> cd
    }.toMap
    components.mapValues(k => WithCategories(k.component, config.categories: _*))
  }

  private def reduceUnique[T](list: Iterable[Map[String, T]]): Map[String, T] = list.foldLeft(Map.empty[String, T]) {
    case (acc, element) =>
      val duplicates = acc.keySet.intersect(element.keySet)
      if (duplicates.isEmpty) {
        acc ++ element
      } else throw new IllegalArgumentException(s"Found duplicate keys: ${duplicates.mkString(", ")}, please correct configuration")
  }
}
