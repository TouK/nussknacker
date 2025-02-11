package pl.touk.nussknacker.engine.definition.component

import cats.data.NonEmptyList
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.component.ComponentsFromProvidersExtractor.componentConfigPath
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object ComponentsFromProvidersExtractor {

  val componentConfigPath = "components"

  def apply(classLoader: ClassLoader): ComponentsFromProvidersExtractor = {
    new ComponentsFromProvidersExtractor(
      classLoader,
      NussknackerVersion.current
    )
  }

}

class ComponentsFromProvidersExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private lazy val providers: Map[String, List[ComponentProvider]] = {
    ScalaServiceLoader
      .load[ComponentProvider](classLoader)
      .groupBy(_.providerName)
  }

  def extractComponents(
      modelDependencies: ProcessObjectDependencies,
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): Components = {
    Components
      .fold(
        componentDefinitionExtractionMode,
        loadCorrectProviders(modelDependencies.config).toList
          .map { case (_, (config, provider)) =>
            extract(
              config,
              provider,
              modelDependencies,
              componentsUiConfig,
              determineDesignerWideId,
              additionalConfigsFromProvider,
              componentDefinitionExtractionMode
            )
          }
      )
  }

  private def loadCorrectProviders(config: Config): Map[String, (ComponentProviderConfig, ComponentProvider)] = {
    val componentsConfig = config.getAs[Map[String, ComponentProviderConfig]](componentConfigPath).getOrElse(Map.empty)
    val manuallyLoadedProvidersWithConfig = loadManuallyLoadedProviders(componentsConfig)
    val autoLoadedProvidersWithConfig     = loadAutoLoadedProviders(componentsConfig, manuallyLoadedProvidersWithConfig)
    manuallyLoadedProvidersWithConfig ++ autoLoadedProvidersWithConfig
  }

  private def loadManuallyLoadedProviders(componentsConfig: Map[String, ComponentProviderConfig]) = {
    componentsConfig.filterNot(_._2.disabled).flatMap { case (name, providerConfig: ComponentProviderConfig) =>
      val providerName = providerConfig.providerType.getOrElse(name)
      val componentProviders = providers.getOrElse(
        providerName,
        throw new IllegalArgumentException(s"Provider $providerName (for component $name) not found")
      )
      NonEmptyList.fromList(componentProviders).map { nel =>
        val provider = findSingleCompatible(name, providerName, nel)
        name -> (providerConfig, provider)
      }
    }
  }

  private def loadAutoLoadedProviders(
      componentsConfig: Map[String, ComponentProviderConfig],
      manuallyLoadedProvidersWithConfig: Map[String, (ComponentProviderConfig, ComponentProvider)]
  ) = {
    val manuallyLoadedProviders = manuallyLoadedProvidersWithConfig.values.map(_._2).toSet
    val autoLoadedProvidersWithConfig = providers.values.flatten
      .filter(provider =>
        provider.isAutoLoaded &&
          !manuallyLoadedProviders.contains(provider) &&
          !componentsConfig.get(provider.providerName).exists(_.disabled)
      )
      .map { provider =>
        if (!provider.isCompatible(nussknackerVersion)) {
          throw new IllegalArgumentException(
            s"Auto-loaded component provider ${provider.providerName} is not compatible with $nussknackerVersion, please use correct component provider version or disable it explicitly."
          )
        }
        provider.providerName -> (ComponentProviderConfig(providerType = None, componentPrefix = None), provider)
      }
    autoLoadedProvidersWithConfig
  }

  def loadAdditionalConfig(inputConfig: Config, configWithDefaults: Config): Config = {
    val resolvedConfigs = loadCorrectProviders(configWithDefaults).map { case (name, (config, provider)) =>
      name -> provider.resolveConfigForExecution(config.config)
    }
    resolvedConfigs.foldLeft(inputConfig) { case (acc, (name, conf)) =>
      // We don't load empty configs to avoid changing the behaviour of components loading.
      // (We don't want auto loaded components to be recognized as manually loaded).
      if (conf.isEmpty) {
        acc
      } else {
        acc.withValue(s"$componentConfigPath.$name", conf.root())
      }
    }
  }

  private def findSingleCompatible(
      name: String,
      providerName: String,
      componentProviders: NonEmptyList[ComponentProvider]
  ): ComponentProvider = {
    val (compatible, incompatible) = componentProviders.toList.partition(_.isCompatible(nussknackerVersion))
    compatible match {
      case Nil =>
        incompatible match {
          case Nil => throw new IllegalArgumentException(s"Provider $providerName (for component $name) not found")
          case _ =>
            throw new IllegalArgumentException(
              s"Component provider $name (of type $providerName) is not compatible with $nussknackerVersion, please use correct component provider version or disable it explicitly."
            )
        }
      case x :: Nil => x
      case _ :: _ =>
        throw new IllegalArgumentException(s"Multiple providers for provider name $providerName (for component $name)")
    }
  }

  private def extract(
      config: ComponentProviderConfig,
      provider: ComponentProvider,
      modelDependencies: ProcessObjectDependencies,
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): Components = {
    val components = provider.create(config.config, modelDependencies).map { inputComponentDefinition =>
      config.componentPrefix
        .map(prefix => inputComponentDefinition.copy(name = prefix + inputComponentDefinition.name))
        .getOrElse(inputComponentDefinition)
    }

    Components.forList(
      components,
      componentsUiConfig,
      determineDesignerWideId,
      additionalConfigsFromProvider,
      componentDefinitionExtractionMode
    )

  }

}
