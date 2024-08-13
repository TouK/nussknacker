package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.Always
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.ui.config.DesignerConfigLoader

trait ProcessingTypesConfig {
  def processingTypeConfigs(): Map[String, ConfigWithUnresolvedVersion]
}

class LoadableConfigBasedProcessingTypesConfig(loadConfig: Always[ConfigWithUnresolvedVersion])
    extends ProcessingTypesConfig {

  import scala.jdk.CollectionConverters._

  private var lastLoadedConfig = loadConfig.value

  override def processingTypeConfigs(): Map[String, ConfigWithUnresolvedVersion] = {
    val config = loadConfig.value
    lastLoadedConfig = config // todo: continue
    read(config, "scenarioTypes").getOrElse {
      throw new RuntimeException("No scenario types configuration provided")
    }
  }

  private def read(
      config: ConfigWithUnresolvedVersion,
      path: String
  ): Option[Map[String, ConfigWithUnresolvedVersion]] = {
    if (config.resolved.hasPath(path)) {
      val nestedConfig = config.getConfig(path)
      Some(
        nestedConfig.resolved
          .root()
          .entrySet()
          .asScala
          .map(_.getKey)
          .map { key => key -> nestedConfig.getConfig(key) }
          .toMap
      )
    } else {
      None
    }
  }

}

class LoadableDesignerConfigBasedProcessingTypesConfig(classLoader: ClassLoader)
    extends LoadableConfigBasedProcessingTypesConfig(Always(DesignerConfigLoader.load(classLoader)))
