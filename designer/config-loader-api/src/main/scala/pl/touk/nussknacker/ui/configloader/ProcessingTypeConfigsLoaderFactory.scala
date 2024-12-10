package pl.touk.nussknacker.ui.configloader

import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeConfigsLoaderFactory {

  def create(
      designerRootConfigLoadedAtStart: DesignerRootConfig,
      sttpBackend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): ProcessingTypeConfigsLoader

}
