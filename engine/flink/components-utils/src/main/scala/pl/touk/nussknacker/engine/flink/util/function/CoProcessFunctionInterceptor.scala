package pl.touk.nussknacker.engine.flink.util.function

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector

/**
 * This class wraps underlying CoProcessFunction and add possibility to add additional behaviour before and after `processElement1`/`processElement2`.
 * It can be used in tests for some kind of synchronization or in production kind for some additional logging, reports and so on.
 */
abstract class CoProcessFunctionInterceptor[IN1, IN2, OUT](underlying: CoProcessFunction[IN1, IN2, OUT])
    extends CoProcessFunction[IN1, IN2, OUT] {

  override def open(openContext: OpenContext): Unit = {
    underlying.open(openContext)
  }

  override def setRuntimeContext(ctx: RuntimeContext): Unit = {
    underlying.setRuntimeContext(ctx)
  }

  override final def processElement1(
      value: IN1,
      ctx: CoProcessFunction[IN1, IN2, OUT]#Context,
      out: Collector[OUT]
  ): Unit = {
    beforeProcessElement1(value)
    underlying.processElement1(value, ctx, out)
    afterProcessElement1(value)
  }

  protected def beforeProcessElement1(value: IN1): Unit = {}

  protected def afterProcessElement1(value: IN1): Unit = {}

  override final def processElement2(
      value: IN2,
      ctx: CoProcessFunction[IN1, IN2, OUT]#Context,
      out: Collector[OUT]
  ): Unit = {
    beforeProcessElement2(value)
    underlying.processElement2(value, ctx, out)
    afterProcessElement2(value)
  }

  protected def beforeProcessElement2(value: IN2): Unit = {}

  protected def afterProcessElement2(value: IN2): Unit = {}

  override def onTimer(
      timestamp: Long,
      ctx: CoProcessFunction[IN1, IN2, OUT]#OnTimerContext,
      out: Collector[OUT]
  ): Unit = {
    underlying.onTimer(timestamp, ctx, out)
  }

  override def close(): Unit = {
    underlying.close()
  }

}
