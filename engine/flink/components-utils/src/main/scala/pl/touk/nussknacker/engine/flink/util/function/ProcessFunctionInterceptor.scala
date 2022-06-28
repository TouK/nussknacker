package pl.touk.nussknacker.engine.flink.util.function

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector

abstract class ProcessFunctionInterceptor[IN, OUT](underlying: KeyedProcessFunction[String, IN, OUT]) extends KeyedProcessFunction[String, IN, OUT] {

  override def open(parameters: Configuration): Unit = {
    underlying.open(parameters)
  }

  override def setRuntimeContext(ctx: RuntimeContext): Unit = {
    underlying.setRuntimeContext(ctx)
  }

  override final def processElement(value: IN, ctx: KeyedProcessFunction[String, IN, OUT]#Context, out: Collector[OUT]): Unit = {
    beforeProcessElement(value)
    underlying.processElement(value, ctx, out)
    afterProcessElement(value)
  }

  protected def beforeProcessElement(value: IN): Unit = {}

  protected def afterProcessElement(value: IN): Unit = {}

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, IN, OUT]#OnTimerContext, out: Collector[OUT]): Unit = {
    underlying.onTimer(timestamp, ctx, out)
  }

  override def close(): Unit = {
    underlying.close()
  }

}
