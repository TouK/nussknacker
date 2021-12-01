package pl.touk.nussknacker.engine.util.metrics.common

import pl.touk.nussknacker.engine.api.EmptyProcessListener
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.exception.ExceptionRateMeter
import pl.touk.nussknacker.engine.util.metrics.WithMetrics

class ExceptionCountingListener extends EmptyProcessListener with WithMetrics {

  private var exceptionRateMeter: ExceptionRateMeter = _

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    exceptionRateMeter = new ExceptionRateMeter(metricsProvider)
  }

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    exceptionRateMeter.markException(exceptionInfo)
  }

}
