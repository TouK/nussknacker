package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

trait FlinkEngineRuntimeContext extends EngineRuntimeContext {
  def runtimeContext: RuntimeContext
}