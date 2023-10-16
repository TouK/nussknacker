package pl.touk.nussknacker.engine.api.component

import com.typesafe.config.Config
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait AdditionalComponentsUIConfigProviderFactory {

  def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): AdditionalComponentsUIConfigProvider

}

class EmptyAdditionalComponentsUIConfigProviderFactory extends AdditionalComponentsUIConfigProviderFactory {

  override def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext,
  ): AdditionalComponentsUIConfigProvider =
    AdditionalComponentsUIConfigProvider.empty

}
