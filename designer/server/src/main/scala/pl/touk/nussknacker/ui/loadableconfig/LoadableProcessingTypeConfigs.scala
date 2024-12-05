package pl.touk.nussknacker.ui.loadableconfig

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.config.ConfigWithUnresolvedVersionExt._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.config.DesignerRootConfig

trait LoadableProcessingTypeConfigs {

  // rootConfigLoadedAtStart is used for external project purpose - don't remove it
  def loadProcessingTypeConfigs(rootConfigLoadedAtStart: DesignerRootConfig): IO[Map[String, ProcessingTypeConfig]]

}

class EachTimeLoadingRootConfigLoadableProcessingTypeConfigs(loadableDesignerRootConfig: LoadableDesignerRootConfig)
    extends LoadableProcessingTypeConfigs {

  def loadProcessingTypeConfigs(
      rootConfigLoadedAtStart: DesignerRootConfig
  ): IO[Map[String, ProcessingTypeConfig]] =
    loadableDesignerRootConfig
      .loadDesignerRootConfig()
      .map(LoadableProcessingTypeConfigs.extractProcessingTypeConfigs)

}

object LoadableProcessingTypeConfigs extends LazyLogging {

  def extractProcessingTypeConfigs(rootConfig: DesignerRootConfig): Map[String, ProcessingTypeConfig] = {
    rootConfig.rawConfig
      .readMap("scenarioTypes")
      .getOrElse {
        throw new RuntimeException("No scenario types configuration provided")
      }
      .mapValuesNow(ProcessingTypeConfig.read)
  }

  def default(loadableDesignerRootConfig: LoadableDesignerRootConfig): LoadableProcessingTypeConfigs = {
    ScalaServiceLoader.load[LoadableProcessingTypeConfigs](getClass.getClassLoader) match {
      case one :: Nil =>
        logger.debug(
          s"Found custom ${classOf[LoadableProcessingTypeConfigs].getSimpleName}: ${one.getClass.getName}. Using it for configuration loading"
        )
        one
      case Nil =>
        logger.debug(s"No custom ${classOf[LoadableProcessingTypeConfigs].getSimpleName} found. Using the default one")
        new EachTimeLoadingRootConfigLoadableProcessingTypeConfigs(loadableDesignerRootConfig)
      case _ =>
        throw new IllegalStateException(s"More than one ${classOf[LoadableProcessingTypeConfigs].getSimpleName} found")
    }
  }

}
