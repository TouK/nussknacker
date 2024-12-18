package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.{Context => NkContext, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher

import java.lang

class EnrichingWithKeyFunction(convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext, nodeId: String)
    extends ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow] {

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
  }

  override def process(
      key: String,
      context: ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow]#Context,
      values: lang.Iterable[AnyRef],
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    values.forEach({ value =>
      out.collect(
        ValueWithContext(value, KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), key))
      )
    })
  }

}

object EnrichingWithKeyFunction {
  def apply(fctx: FlinkCustomNodeContext): EnrichingWithKeyFunction =
    new EnrichingWithKeyFunction(fctx.convertToEngineRuntimeContext, fctx.nodeId)
}
