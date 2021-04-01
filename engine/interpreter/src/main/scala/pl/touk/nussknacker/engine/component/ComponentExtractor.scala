package pl.touk.nussknacker.engine.component

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.component.{Component, ComponentConfig, ComponentDefinition, ComponentProvider, ComponentProviderConfig, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

class ComponentExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private val providers = ScalaServiceLoader.load[ComponentProvider](classLoader).map(p => p.providerName -> p).toMap

  def extract(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Component]] = {
    implicit val reader: ValueReader[ComponentProviderConfig] = null
    val componentsConfig = processObjectDependencies.config.as[Map[String, ComponentProviderConfig]]("components")
    componentsConfig.map {
      case (name, providerConfig: ComponentProviderConfig) => extractOneProvider(name, providerConfig, processObjectDependencies)
    }.reduce(_ ++ _)
  }

  private def extractOneProvider(name: String, config: ComponentProviderConfig, processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Component]] = {
    val providerName = config.providerType.getOrElse(name)
    val provider = providers.getOrElse(providerName, throw new IllegalArgumentException(s"Provider $providerName not found"))
    if (config.disabled || !provider.isCompatible(nussknackerVersion)) {
      Map.empty
    } else {
      extractOneProviderConfig(config, provider, processObjectDependencies)
    }
  }

  private def extractOneProviderConfig(config: ComponentProviderConfig, provider: ComponentProvider, processObjectDependencies: ProcessObjectDependencies) = {
    val components = provider.create(config.config, processObjectDependencies).map(cd => cd.name -> cd).toMap

    val componentElements = config.componentsConfig

    //if component does not have configuration, but is enabled by default we initialize it with empty config
    val enabledByDefault = components.collect {
      case (name, ComponentDefinition(_, true, create)) if !componentElements.contains(name) =>
        name -> create(ConfigFactory.empty(), None)
    }
    val other = componentElements.map {
      case (name, ComponentConfig(componentOriginalName, false, config, nodeConfig)) =>
        val componentName = componentOriginalName.getOrElse(name)
        val componentData = components.getOrElse(componentName, throw new IllegalArgumentException(s"Failed to find $componentName"))
        val componentToUse = componentData.create(config, nodeConfig)
        name -> componentToUse
    }
    (enabledByDefault ++ other).mapValues(k => WithCategories(k._1, config.categories, k._2.getOrElse(SingleNodeConfig.zero)))
  }
}
