package pl.touk.nussknacker.ui.config.processingtype

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.config.root.LoadableDesignerRootConfig
import pl.touk.nussknacker.ui.loadableconfig.LoadableProcessingTypeConfigs

object LoadableProcessingTypeConfigsFactory extends LazyLogging {

  def create(loadableDesignerRootConfig: LoadableDesignerRootConfig): LoadableProcessingTypeConfigs = {
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
