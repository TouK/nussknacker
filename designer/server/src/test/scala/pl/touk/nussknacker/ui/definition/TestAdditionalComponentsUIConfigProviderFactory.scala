package pl.touk.nussknacker.ui.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{
  AdditionalComponentsUIConfigProvider,
  AdditionalComponentsUIConfigProviderFactory
}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class TestAdditionalComponentsUIConfigProviderFactory extends AdditionalComponentsUIConfigProviderFactory {

  override def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AdditionalComponentsUIConfigProvider = TestAdditionalComponentsUIConfigProvider

}
