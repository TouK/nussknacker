package pl.touk.nussknacker.engine.component

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.config.LoadedConfig
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.component.ComponentExtractor.{ComponentLoadResult, componentConfigPath}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object ComponentExtractor {

  val componentConfigPath = "components"

  def apply(classLoader: ClassLoader): ComponentExtractor = {
    ComponentExtractor(classLoader, NussknackerVersion.current)
  }

  private case class ComponentLoadResult(loadedProviderConfig: ComponentProviderConfig, unresolvedConfig: Config, provider: ComponentProvider) {
    def loadedConfig: LoadedConfig = LoadedConfig(loadedProviderConfig.config, unresolvedConfig)
  }

}

case class ComponentExtractor(classLoader: ClassLoader, nussknackerVersion: NussknackerVersion) {

  private val providers = ScalaServiceLoader.load[ComponentProvider](classLoader).map(p => p.providerName -> p).toMap

  private def loadCorrectComponents(config: LoadedConfig): Map[String, ComponentLoadResult] = {
    config.getOpt(componentConfigPath).map { componentsConfig =>
      componentsConfig.entries.map {
        case (name, loadedProviderConfig) =>
          // we need to hold both loaded config (for additional config resolution) and unresolved version (to keep passed to
          // executor config concise and to defer environment variables resolution to executor)
          name -> (componentsConfig.loadedConfig.as[ComponentProviderConfig](name), loadedProviderConfig.unresolvedConfig.config)
      }.filterNot(_._2._1.disabled).map {
        case (name, (providerConfig, unresolvedConfig)) =>
          val providerName = providerConfig.providerType.getOrElse(name)
          val provider = providers.getOrElse(providerName, throw new IllegalArgumentException(s"Provider $providerName (for component $name) not found. Available providers: ${providers.keys.mkString(", ")}"))
          if (!provider.isCompatible(nussknackerVersion)) {
            throw new IllegalArgumentException(s"Component provider $name (of type $providerName) is not compatible with $nussknackerVersion, please use correct component provider version or disable it explicitly.")
          }
          name -> ComponentLoadResult(providerConfig, unresolvedConfig, provider)
      }
    }
  }.getOrElse(Map.empty)

  def extract(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Component]] =
    loadCorrectComponents(LoadedConfig(processObjectDependencies.config, processObjectDependencies.config)).map {
      case (_, loadResult) => extractOneProviderConfig(loadResult.loadedProviderConfig, loadResult.provider, processObjectDependencies)
    }.reduceUnique

  def loadAdditionalConfig(inputConfig: LoadedConfig): Config = {
    val resolvedByComponentProvidersConfigs = loadCorrectComponents(inputConfig).map {
      case (name, loadResult) => name -> loadResult.provider.resolveConfigForExecution(loadResult.loadedConfig)
    }
    resolvedByComponentProvidersConfigs.foldLeft(inputConfig.unresolvedConfig.config) {
      case (acc, (name, conf)) => acc.withValue(s"$componentConfigPath.$name", conf.root())
    }
  }

  private def extractOneProviderConfig(config: ComponentProviderConfig, provider: ComponentProvider, processObjectDependencies: ProcessObjectDependencies) = {
    val components = provider.create(config.config, processObjectDependencies).map { cd =>
      val finalName = config.componentPrefix.map(_ + cd.name).getOrElse(cd.name)
      finalName -> cd
    }.toMap
    components.mapValues(k => WithCategories(k.component, config.categories, SingleNodeConfig.zero.copy(docsUrl = k.docsUrl, icon = k.icon)))
  }

}
