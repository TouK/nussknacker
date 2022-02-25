package pl.touk.nussknacker.engine.util.definition

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

trait RuntimeInjectedJobData extends WithJobData with Lifecycle {
  private var engineRuntimeContextOpt:Option[EngineRuntimeContext] = None

  override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
    super.open(engineRuntimeContext)
    engineRuntimeContextOpt = Some(engineRuntimeContext)
  }

  override def jobData: JobData = engineRuntimeContextOpt.getOrElse(throw new UninitializedJobDataException).jobData
}

class UninitializedJobDataException extends IllegalStateException







