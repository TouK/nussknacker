package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}

trait FlinkEngineRuntimeContext extends EngineRuntimeContext {
  def runtimeContext: RuntimeContext
}

trait RuntimeContextLifecycle extends EngineRuntimeContextLifecycle {

  final override def open(context: EngineRuntimeContext): Unit = {
    openWithFlinkContext(context.asInstanceOf[FlinkEngineRuntimeContext])
  }

  def openWithFlinkContext(runtimeContext: FlinkEngineRuntimeContext): Unit = {}

}
