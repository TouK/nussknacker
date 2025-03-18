package pl.touk.nussknacker.ui.config

import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  AdditionalUIConfigProviderFactory,
  EmptyAdditionalUIConfigProviderFactory
}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

object AdditionalUIConfigProviderLoader {

  def loadAdditionalUIConfigProvider(designerConfig: DesignerConfig, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AdditionalUIConfigProvider = {
    val additionalUIConfigProviderFactory: AdditionalUIConfigProviderFactory = {
      Multiplicity(
        ScalaServiceLoader.load[AdditionalUIConfigProviderFactory](getClass.getClassLoader)
      ) match {
        case Empty()              => new EmptyAdditionalUIConfigProviderFactory
        case One(providerFactory) => providerFactory
        case Many(moreThanOne) =>
          throw new IllegalArgumentException(
            s"More than one AdditionalUIConfigProviderFactory instance found: $moreThanOne"
          )
      }
    }

    additionalUIConfigProviderFactory.create(designerConfig.rawConfig, sttpBackend)
  }

}
