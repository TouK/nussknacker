package pl.touk.nussknacker.ui.configloader

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import sttp.client3.SttpBackend

trait ProcessingTypeConfigsLoaderFactory {

  def create(
      configLoaderConfig: Config,
      sttpBackend: SttpBackend[IO, Any],
  )(implicit ec: IORuntime): ProcessingTypeConfigsLoader

}
