package pl.touk.nussknacker.ui.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.additionalconfig.{AdditionalUIConfigProvider, AdditionalUIConfigProviderFactory}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class TestAdditionalUIConfigProviderFactory extends AdditionalUIConfigProviderFactory {

  override def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AdditionalUIConfigProvider = TestAdditionalUIConfigProvider

}
