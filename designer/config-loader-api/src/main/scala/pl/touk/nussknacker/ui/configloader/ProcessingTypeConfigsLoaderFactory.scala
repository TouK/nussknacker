package pl.touk.nussknacker.ui.configloader

import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeConfigsLoaderFactory {

  def create(
      designerConfigLoadedAtStart: DesignerConfig,
      sttpBackend: SttpBackend[Future, Any],
  )(implicit ec: ExecutionContext): ProcessingTypeConfigsLoader

}
