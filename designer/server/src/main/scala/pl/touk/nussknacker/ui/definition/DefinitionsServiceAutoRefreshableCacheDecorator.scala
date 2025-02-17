package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.DefinitionsService.ComponentUiConfigMode
import pl.touk.nussknacker.ui.definition.DefinitionsServiceAutoRefreshableCacheDecorator.CacheKey
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.AutoRefreshableCache

import scala.concurrent.Future

class DefinitionsServiceAutoRefreshableCacheDecorator(
    underlying: DefinitionsService,
    cache: AutoRefreshableCache[CacheKey, UIDefinitions],
) extends DefinitionsService {

  override def prepareUIDefinitions(
      processingType: ProcessingType,
      forFragment: Boolean,
      componentUiConfigMode: ComponentUiConfigMode
  )(implicit user: LoggedUser): Future[UIDefinitions] =
    cache.getIfPresentOrPut(
      (processingType, forFragment, componentUiConfigMode),
      () => underlying.prepareUIDefinitions(processingType, forFragment, componentUiConfigMode)
    )

}

object DefinitionsServiceAutoRefreshableCacheDecorator {
  type CacheKey = (ProcessingType, Boolean, ComponentUiConfigMode)
}
