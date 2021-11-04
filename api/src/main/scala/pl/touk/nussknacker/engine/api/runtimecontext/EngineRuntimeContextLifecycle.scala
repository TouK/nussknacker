package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.api.JobData

trait EngineRuntimeContextLifecycle {

  def open(jobData: JobData, context: EngineRuntimeContext): Unit = {}

}
