package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher

class EnrichingWithKeyFunction(convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext, nodeId: String) extends ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow] {

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(parameters: Configuration): Unit = {
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
  }

  override def process(key: String, context: Context, values: Iterable[AnyRef], out: Collector[ValueWithContext[AnyRef]]): Unit = {
    values.foreach { value =>
      out.collect(ValueWithContext(value, KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), key)))
    }
  }
}

object EnrichingWithKeyFunction {
  def apply(fctx: FlinkCustomNodeContext): EnrichingWithKeyFunction = new EnrichingWithKeyFunction(fctx.convertToEngineRuntimeContext, fctx.nodeId)
}