package pl.touk.nussknacker.engine.component

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.component.ComponentExtractor.componentConfigPath
import pl.touk.nussknacker.engine.util.Implicits.RichIterableMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object ComponentExtractor {

  val componentConfigPath = "components"

  def apply(classLoader: ClassLoader): ComponentExtractor = {
    ComponentExtractor(classLoader, NussknackerVersion.current)
  }

}

case class ComponentExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private lazy val providers = ScalaServiceLoader.load[ComponentProvider](classLoader).map(p => p.providerName -> p).toMap

  private def loadCorrectProviders(config: Config): Map[String, (ComponentProviderConfig, ComponentProvider)] = {
    val componentsConfig = config.getAs[Map[String, ComponentProviderConfig]](componentConfigPath).getOrElse(Map.empty)
    val manuallyLoadedProvidersWithConfig = loadManuallyLoadedProviders(componentsConfig)
    val autoLoadedProvidersWithConfig = loadAutoLoadedProviders(componentsConfig, manuallyLoadedProvidersWithConfig)
    manuallyLoadedProvidersWithConfig ++ autoLoadedProvidersWithConfig
  }

  private def loadManuallyLoadedProviders(componentsConfig: Map[String, ComponentProviderConfig]) = {
    componentsConfig.filterNot(_._2.disabled).map {
      case (name, providerConfig: ComponentProviderConfig) =>
        val providerName = providerConfig.providerType.getOrElse(name)
        val provider = providers.getOrElse(providerName, throw new IllegalArgumentException(s"Provider $providerName (for component $name) not found"))
        if (!provider.isCompatible(nussknackerVersion)) {
          throw new IllegalArgumentException(s"Component provider $name (of type $providerName) is not compatible with $nussknackerVersion, please use correct component provider version or disable it explicitly.")
        }
        name -> (providerConfig, provider)
    }
  }

  private def loadAutoLoadedProviders(componentsConfig: Map[String, ComponentProviderConfig], manuallyLoadedProvidersWithConfig: Map[String, (ComponentProviderConfig, ComponentProvider)]) = {
    val manuallyLoadedProviders = manuallyLoadedProvidersWithConfig.values.map(_._2).toSet
    val autoLoadedProvidersWithConfig = providers.values
      .filter(provider => provider.isAutoLoaded && !manuallyLoadedProviders.contains(provider) && !componentsConfig.get(provider.providerName).exists(_.disabled))
      .map { provider =>
        if (!provider.isCompatible(nussknackerVersion)) {
          throw new IllegalArgumentException(s"Auto-loaded component provider ${provider.providerName} is not compatible with $nussknackerVersion, please use correct component provider version or disable it explicitly.")
        }
        provider.providerName -> (ComponentProviderConfig(providerType = None, componentPrefix = None), provider)
      }
    autoLoadedProvidersWithConfig
  }

  def extract(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Component]] =
    loadCorrectProviders(processObjectDependencies.config).map {
      case (_, (config, provider)) => extractOneProviderConfig(config, provider, processObjectDependencies)
    }.reduceUnique

  def loadAdditionalConfig(inputConfig: Config, configWithDefaults: Config): Config = {
    val resolvedConfigs = loadCorrectProviders(configWithDefaults).map {
      case (name, (config, provider)) => name -> provider.resolveConfigForExecution(config.config)
    }
    resolvedConfigs.foldLeft(inputConfig) {
      case (acc, (name, conf)) => acc.withValue(s"$componentConfigPath.$name", conf.root())
    }
  }

  private def extractOneProviderConfig(config: ComponentProviderConfig, provider: ComponentProvider, processObjectDependencies: ProcessObjectDependencies) = {
    val components = provider.create(config.config, processObjectDependencies).map { cd =>
      val finalName = config.componentPrefix.map(_ + cd.name).getOrElse(cd.name)
      finalName -> cd
    }.toMap
    components.mapValues(k => WithCategories(k.component, config.categories, SingleComponentConfig.zero.copy(docsUrl = k.docsUrl, icon = k.icon)))
  }

}
