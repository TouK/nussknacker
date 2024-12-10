package pl.touk.nussknacker.ui.config.processingtype

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.config.root.DesignerRootConfigLoader
import pl.touk.nussknacker.ui.configloader.{
  DesignerRootConfig,
  ProcessingTypeConfigsLoader,
  ProcessingTypeConfigsLoaderFactory
}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeConfigsLoaderFactoryServiceLoader extends LazyLogging {

  def loadService(designerRootConfigLoader: DesignerRootConfigLoader): ProcessingTypeConfigsLoaderFactory = {
    ScalaServiceLoader.load[ProcessingTypeConfigsLoaderFactory](getClass.getClassLoader) match {
      case one :: Nil =>
        logger.debug(
          s"Found custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName}: ${one.getClass.getName}. Using it for configuration loading"
        )
        one
      case Nil =>
        logger.debug(
          s"No custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName} found. Using the default one"
        )
        (_: DesignerRootConfig, _: SttpBackend[Future, Any], _: ExecutionContext) =>
          new EachTimeLoadingRootConfigProcessingTypeConfigsLoader(designerRootConfigLoader)
      case _ =>
        throw new IllegalStateException(s"More than one ${classOf[ProcessingTypeConfigsLoader].getSimpleName} found")
    }
  }

}
