package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.DefinitionsServiceAutoRefreshableCacheDecorator.CacheKey
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.util.AutoRefreshableCache

import scala.concurrent.ExecutionContext

// We need to invalidate UIDefinitions cache when there is new fragment saved
class DefinitionsServiceProcessChangeListener(cache: AutoRefreshableCache[CacheKey, UIDefinitions])
    extends ProcessChangeListener {

  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = {
    event match {
      case ProcessChangeEvent.OnSaved(_, _, isFragment) if isFragment => cache.invalidateAll()
      case _                                                          => ()
    }
  }

}
