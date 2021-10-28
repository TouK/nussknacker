package pl.touk.nussknacker.engine.component

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.component.ComponentExtractor.{ComponentsGroupedByType, componentConfigPath}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.reflect.ClassTag

object ComponentExtractor {

  val componentConfigPath = "components"

  def apply(classLoader: ClassLoader): ComponentExtractor = {
    ComponentExtractor(classLoader, NussknackerVersion.current)
  }

  case class ComponentsGroupedByType(services: Map[String, WithCategories[Service]],
                                     sourceFactories: Map[String, WithCategories[SourceFactory[_]]],
                                     sinkFactories: Map[String, WithCategories[SinkFactory]],
                                     customTransformers: Map[String, WithCategories[CustomStreamTransformer]])
}

case class ComponentExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private lazy val providers: Map[String, ComponentProvider] = {
    val componentProviders = ScalaServiceLoader
      .load[ComponentProvider](classLoader)
      .map(p => p.providerName -> p)
    checkProviderDuplicates(componentProviders)
    componentProviders.toMap
  }

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

  def extractComponents(processObjectDependencies: ProcessObjectDependencies): ComponentsGroupedByType = {
    val components = loadCorrectProviders(processObjectDependencies.config)
      .toList
      .flatMap { case (_, (config, provider)) => extractOneProviderConfig(config, provider, processObjectDependencies) }
    groupByComponentType(components)
  }


  def loadAdditionalConfig(inputConfig: Config, configWithDefaults: Config): Config = {
    val resolvedConfigs = loadCorrectProviders(configWithDefaults).map {
      case (name, (config, provider)) => name -> provider.resolveConfigForExecution(config.config)
    }
    resolvedConfigs.foldLeft(inputConfig) {
      case (acc, (name, conf)) => acc.withValue(s"$componentConfigPath.$name", conf.root())
    }
  }

  private def extractOneProviderConfig(config: ComponentProviderConfig, provider: ComponentProvider, processObjectDependencies: ProcessObjectDependencies): List[(String, WithCategories[Component])] = {
    provider.create(config.config, processObjectDependencies).map { cd =>
      val finalName = config.componentPrefix.map(_ + cd.name).getOrElse(cd.name)
      finalName -> WithCategories(cd.component, config.categories, SingleComponentConfig.zero.copy(docsUrl = cd.docsUrl, icon = cd.icon))
    }
  }

  private def groupByComponentType(definitions: List[(String, WithCategories[Component])]) = {
    def checkDuplicates[T <: Component : ClassTag](components: List[(String, WithCategories[Component])]) = {
      components.groupBy(_._1)
        .collect { case (_, duplicatedComponents) =>
          if (duplicatedComponents.length > 1) {
            throw new IllegalArgumentException(s"Found duplicate keys: ${duplicatedComponents.mkString(", ")}, please correct configuration")
          }
        }
    }

    def forClass[T <: Component : ClassTag] = {
      val defs = definitions.collect {
        case (id, a@WithCategories(definition: T, _, _)) => id -> a.copy(value = definition)
      }
      checkDuplicates(defs)
      defs.toMap
    }

    ComponentsGroupedByType(
      services = forClass[Service],
      sourceFactories = forClass[SourceFactory[_]],
      sinkFactories = forClass[SinkFactory],
      customTransformers = forClass[CustomStreamTransformer])
  }

  private def checkProviderDuplicates(componentProviders: List[(String, _)]): Unit = {
    componentProviders.groupBy(_._1)
      .foreach { case (_, duplicatedProviders) =>
        if (duplicatedProviders.length > 1) {
          throw new IllegalArgumentException(s"Found duplicated providers: ${duplicatedProviders.map(_._1).distinct.mkString(", ")}, please correct configuration")
        }
      }
  }

}
