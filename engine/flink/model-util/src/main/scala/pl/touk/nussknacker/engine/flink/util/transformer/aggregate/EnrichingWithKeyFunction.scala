package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher

class EnrichingWithKeyFunction extends ProcessWindowFunction[AnyRef, ValueWithContext[AnyRef], String, TimeWindow] {
  override def process(key: String, context: Context, values: Iterable[AnyRef], out: Collector[ValueWithContext[AnyRef]]): Unit = {
    values.foreach { value =>
      out.collect(ValueWithContext(value, KeyEnricher.enrichWithKey(NkContext.withInitialId, key)))
    }
  }
}
