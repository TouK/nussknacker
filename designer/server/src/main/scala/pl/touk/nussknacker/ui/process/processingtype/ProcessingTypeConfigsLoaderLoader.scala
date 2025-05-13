package pl.touk.nussknacker.ui.process.processingtype

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.config.{DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.configloader.{ProcessingTypeConfigsLoader, ProcessingTypeConfigsLoaderFactory}
import sttp.client3.SttpBackend

object ProcessingTypeConfigsLoaderLoader extends LazyLogging {

  def createProcessingTypeConfigsLoader(
      designerConfigLoader: DesignerConfigLoader,
      designerConfig: DesignerConfig,
      sttpBackend: SttpBackend[IO, Any]
  )(implicit ioRuntime: IORuntime): ProcessingTypeConfigsLoader = {
    ScalaServiceLoader
      .loadOne[ProcessingTypeConfigsLoaderFactory](getClass.getClassLoader)
      .map { factory =>
        logger.debug(
          s"Found custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName}: ${factory.getClass.getName}. Using it for configuration loading"
        )
        factory.create(
          designerConfig.configLoaderConfig,
          designerConfigLoader.loadDesignerConfig().map(_.processingTypeConfigsRaw().resolved),
          sttpBackend
        )
      }
      .getOrElse {
        logger.debug(
          s"No custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName} found. Using the default implementation of loader"
        )
        () => designerConfigLoader.loadDesignerConfig().map(_.processingTypeConfigs())
      }
  }

}
