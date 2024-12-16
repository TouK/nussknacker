package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.{AbstractRichFunction, MapFunction, OpenContext, RuntimeContext}
import pl.touk.nussknacker.engine.api.Lifecycle
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

abstract class RichLifecycleFunction extends AbstractRichFunction {

  protected def lifecycle: Lifecycle

  protected val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext

  override def open(openContext: OpenContext): Unit = {
    lifecycle.open(convertToEngineRuntimeContext(getRuntimeContext))
  }

  override def close(): Unit = {
    lifecycle.close()
  }

}

class RichLifecycleMapFunction[T, R](
    protected override val lifecycle: (T => R) with Lifecycle,
    protected override val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends RichLifecycleFunction
    with MapFunction[T, R] {

  override def map(value: T): R = lifecycle(value)

}
