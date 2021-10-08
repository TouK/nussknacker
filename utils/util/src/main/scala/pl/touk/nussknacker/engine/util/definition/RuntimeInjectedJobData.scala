package pl.touk.nussknacker.engine.util.definition

import pl.touk.nussknacker.engine.api._

trait RuntimeInjectedJobData extends WithJobData with Lifecycle {
  private var optionJobData:Option[JobData] = None

  override def open(jobData: JobData): Unit = {
    super.open(jobData)
    optionJobData = Some(jobData)
  }

  override def jobData: JobData = optionJobData.getOrElse(throw new UninitializedJobDataException)
}

class UninitializedJobDataException extends IllegalStateException







