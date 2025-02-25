package pl.touk.nussknacker.engine.management.utils

import org.apache.flink.api.common.JobID

import java.util.UUID

object JobIdGenerator {

  def generateJobId: JobID = {
    val uuid = UUID.randomUUID()
    new JobID(uuid.getLeastSignificantBits, uuid.getLeastSignificantBits)
  }

}
