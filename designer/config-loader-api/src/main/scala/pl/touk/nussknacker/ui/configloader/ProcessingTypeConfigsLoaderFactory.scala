package pl.touk.nussknacker.ui.configloader

import cats.effect.IO
import com.typesafe.config.Config
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeConfigsLoaderFactory {

  def create(
      configLoaderConfig: Config,
      sttpBackend: SttpBackend[IO, Any],
  )(implicit ec: ExecutionContext): ProcessingTypeConfigsLoader

}
