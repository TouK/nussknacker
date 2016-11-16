package pl.touk.esp.engine.flink.util.exception

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter

class RateMeterExceptionConsumer(underlying: FlinkEspExceptionConsumer) extends FlinkEspExceptionConsumer {

  private lazy val instantRateMeter = new InstantRateMeter

  override def open(runtimeContext: RuntimeContext) = {
    underlying.open(runtimeContext)
    runtimeContext.getMetricGroup
      .addGroup("error")
      .gauge[Double, InstantRateMeter]("instantRate", instantRateMeter)
  }

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]) = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      instantRateMeter.mark()
    }
  }

  override def close() = {
    underlying.close()
  }

}
