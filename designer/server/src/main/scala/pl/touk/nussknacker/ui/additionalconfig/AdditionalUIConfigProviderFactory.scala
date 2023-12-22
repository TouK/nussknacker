package pl.touk.nussknacker.ui.additionalconfig

import com.typesafe.config.Config
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait AdditionalUIConfigProviderFactory {

  def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): AdditionalUIConfigProvider

}

class EmptyAdditionalUIConfigProviderFactory extends AdditionalUIConfigProviderFactory {

  override def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): AdditionalUIConfigProvider = AdditionalUIConfigProvider.empty

}
