package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}

trait FlinkEngineRuntimeContext extends EngineRuntimeContext {
  def runtimeContext: RuntimeContext
}

//TODO: is it needed ATM?
trait RuntimeContextLifecycle extends EngineRuntimeContextLifecycle {

  override def open(jobData: JobData, context: EngineRuntimeContext): Unit = {
    open(context.asInstanceOf[FlinkEngineRuntimeContext].runtimeContext)
  }

  def open(runtimeContext: RuntimeContext): Unit = {}

}
