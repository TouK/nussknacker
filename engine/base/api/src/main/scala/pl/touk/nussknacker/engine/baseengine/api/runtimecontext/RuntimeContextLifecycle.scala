package pl.touk.nussknacker.engine.baseengine.api.runtimecontext

import pl.touk.nussknacker.engine.api.JobData

trait RuntimeContextLifecycle {

  def open(jobData: JobData, context: EngineRuntimeContext): Unit

  def close(): Unit


}
