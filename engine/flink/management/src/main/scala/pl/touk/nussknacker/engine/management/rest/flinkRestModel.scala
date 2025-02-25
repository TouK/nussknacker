package pl.touk.nussknacker.engine.management.rest

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import org.apache.flink.api.common
import org.apache.flink.api.common.JobID

object flinkRestModel {

  @JsonCodec(encodeOnly = true) case class DeployProcessRequest(
      entryClass: String,
      savepointPath: Option[String],
      programArgsList: List[String],
      parallelism: Int = common.ExecutionConfig.PARALLELISM_DEFAULT,
      allowNonRestoredState: Boolean = true,
      jobId: Option[JobID]
  )

  @JsonCodec(encodeOnly = true) case class SavepointTriggerRequest(
      `target-directory`: Option[String],
      `cancel-job`: Boolean
  )

  @JsonCodec(decodeOnly = true) case class SavepointTriggerResponse(`request-id`: String)

  @JsonCodec(decodeOnly = true) case class GetSavepointStatusResponse(
      status: SavepointStatus,
      operation: Option[SavepointOperation]
  ) {

    def isCompletedSuccessfully: Boolean = status.isCompleted && operation.flatMap(_.location).isDefined

    def isFailed: Boolean = status.isCompleted && !isCompletedSuccessfully

  }

  @JsonCodec(decodeOnly = true) case class SavepointOperation(
      location: Option[String],
      `failure-cause`: Option[FailureCause]
  )

  @JsonCodec(decodeOnly = true) case class FailureCause(
      `class`: Option[String],
      `stack-trace`: Option[String],
      `serialized-throwable`: Option[String]
  )

  @JsonCodec(decodeOnly = true) case class SavepointStatus(id: String) {
    def isCompleted: Boolean = id == "COMPLETED"
  }

  @JsonCodec(decodeOnly = true) case class JobsResponse(jobs: List[JobOverview])

  // NOTE: Flink <1.10 compatibility - JobStatus changed package, so we use String here
  @JsonCodec(decodeOnly = true) case class JobOverview(
      jid: JobID,
      name: String,
      `last-modification`: Long,
      `start-time`: Long,
      state: String,
      tasks: JobTasksOverview
  )

  @JsonCodec(decodeOnly = true) case class JobDetails(
      state: String,
      `status-counts`: JobStatusCounts
  )

  @JsonCodec(decodeOnly = true) case class JobTasksOverview(
      override val total: Int,
      created: Int,
      scheduled: Int,
      deploying: Int,
      override val running: Int,
      override val finished: Int,
      canceling: Int,
      canceled: Int,
      failed: Int,
      reconciling: Int,
      initializing: Option[Int]
  ) extends BaseJobStatusCounts

  @JsonCodec(decodeOnly = true) case class JobStatusCounts(
      CREATED: Int,
      SCHEDULED: Int,
      DEPLOYING: Int,
      RUNNING: Int,
      FINISHED: Int,
      CANCELING: Int,
      CANCELED: Int,
      FAILED: Int,
      RECONCILING: Int,
      INITIALIZING: Option[Int]
  ) extends BaseJobStatusCounts {
    override def running: Int = RUNNING

    override def finished: Int = FINISHED

    override def total: Int =
      CREATED + SCHEDULED + DEPLOYING + RUNNING + FINISHED + CANCELING + CANCELED + FAILED + RECONCILING + INITIALIZING
        .getOrElse(0)

  }

  trait BaseJobStatusCounts {
    def running: Int

    def finished: Int

    def total: Int
  }

  @JsonCodec(decodeOnly = true) case class JobConfig(jid: JobID, `execution-config`: ExecutionConfig)

  @JsonCodec(decodeOnly = true) case class ExecutionConfig(
      `job-parallelism`: Int,
      `user-config`: Map[String, io.circe.Json]
  )

  @JsonCodec(decodeOnly = true) case class JarsResponse(files: Option[List[JarFile]])

  @JsonCodec(decodeOnly = true) case class UploadJarResponse(filename: String)

  @JsonCodec(decodeOnly = true) case class JarFile(id: String, name: String)

  @JsonCodec(decodeOnly = true) case class ClusterOverview(`slots-total`: Int, `slots-available`: Int)

  @JsonCodec(decodeOnly = true) case class KeyValueEntry(key: String, value: String)

  @JsonCodec case class RunResponse(jobid: JobID)

  implicit val jobIdEncoder: Encoder[JobID] = Encoder[String].contramap[JobID](_.toHexString)
  implicit val jobIdDecoder: Decoder[JobID] = Decoder[String].map(JobID.fromHexString)

}
