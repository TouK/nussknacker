package pl.touk.nussknacker.engine.api.fixedvaluespresets

import com.typesafe.config.Config
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait FixedValuesPresetProviderFactory {

  def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): FixedValuesPresetProvider

}
