package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.api.{JobData, Lifecycle}
import pl.touk.nussknacker.engine.compiledgraph.service.ServiceRef

class LifecycleHandler(globals: Seq[Lifecycle], services: List[ServiceRef]) {

  def close(): Unit = withLifecycle(_.close())

  def open(jobData: JobData)(partialHandler: PartialFunction[Lifecycle, Unit] = Map.empty): Unit =
    withLifecycle(partialHandler.orElse { case k => k.open(jobData) })

  def withLifecycle(action: Lifecycle => Unit): Unit = (globals ++ services.map(_.invoker)).foreach(action)

}
