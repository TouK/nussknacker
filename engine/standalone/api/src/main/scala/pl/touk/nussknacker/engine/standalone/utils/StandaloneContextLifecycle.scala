package pl.touk.nussknacker.engine.standalone.utils

import pl.touk.nussknacker.engine.api.JobData

trait StandaloneContextLifecycle {

  def open(jobData: JobData, context: StandaloneContext): Unit

  def close(): Unit


}
