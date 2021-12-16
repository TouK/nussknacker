package pl.touk.nussknacker.engine.process.exception

import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.util.exception.ExceptionRateMeter
import pl.touk.nussknacker.engine.util.metrics.WithMetrics

//For Flink we count metrics in ExceptionHandler, not e.g. in NodeCountListener, so that it's easier to
//handle errors consistently in CustomStreamTransformers
class RateMeterExceptionConsumer(val underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer with WithMetrics {

  @transient private var exceptionRateMeter: ExceptionRateMeter = _

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      exceptionRateMeter.markException(exceptionInfo)
    }
  }

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    underlying.open(context)
    exceptionRateMeter = new ExceptionRateMeter(metricsProvider)
  }

  override def close(): Unit = {
    super.close()
    underlying.close()
  }

}
