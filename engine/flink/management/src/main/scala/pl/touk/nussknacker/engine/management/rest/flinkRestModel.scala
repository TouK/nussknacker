package pl.touk.nussknacker.engine.management.rest

import io.circe.derivation.annotations.Configuration.decodeOnly
import io.circe.derivation.annotations.{Configuration, JsonCodec}
import org.apache.flink.api.common

object flinkRestModel {

  /*
  When #programArgsList is not set in request Flink warns that
  <pre>Configuring the job submission via query parameters is deprecated. Please migrate to submitting a JSON request instead.</pre>
  But now we can't add #programArgsList support because of back compatibility of Flink 1.6..
   */
  @JsonCodec case class DeployProcessRequest(entryClass: String,
                                                                savepointPath: Option[String],
                                                                programArgs: String,
                                                                parallelism: Int = common.ExecutionConfig.PARALLELISM_DEFAULT,
                                                                allowNonRestoredState: Boolean = true)

  @JsonCodec case class SavepointTriggerRequest(`target-directory`: Option[String], `cancel-job`: Boolean)

  @JsonCodec case class StopRequest(targetDirectory: Option[String], drain: Boolean)

  @JsonCodec(Configuration.decodeOnly) case class SavepointTriggerResponse(`request-id`: String)

  @JsonCodec(decodeOnly) case class GetSavepointStatusResponse(status: SavepointStatus, operation: Option[SavepointOperation]) {

    def isCompletedSuccessfully: Boolean = status.isCompleted && operation.flatMap(_.location).isDefined

    def isFailed: Boolean = status.isCompleted && !isCompletedSuccessfully

  }

  @JsonCodec(decodeOnly) case class SavepointOperation(location: Option[String], `failure-cause`: Option[FailureCause])

  @JsonCodec(decodeOnly) case class FailureCause(`class`: Option[String], `stack-trace`: Option[String], `serialized-throwable`: Option[String])

  @JsonCodec(decodeOnly) case class SavepointStatus(id: String) {
    def isCompleted: Boolean = id == "COMPLETED"
  }

  @JsonCodec(decodeOnly) case class JobsResponse(jobs: List[JobOverview])

  //NOTE: Flink <1.10 compatibility - JobStatus changed package, so we use String here
  @JsonCodec(decodeOnly) case class JobOverview(jid: String, name: String, `last-modification`: Long, `start-time`: Long, state: String, tasks: JobTasksOverview)

  @JsonCodec(decodeOnly) case class JobTasksOverview(total: Int, created: Int, scheduled: Int, deploying: Int, running: Int, finished: Int,
                                                            canceling: Int, canceled: Int, failed: Int, reconciling: Int, initializing: Option[Int])

  @JsonCodec(decodeOnly) case class JobConfig(jid: String, `execution-config`: ExecutionConfig)

  @JsonCodec(decodeOnly) case class ExecutionConfig(`job-parallelism`: Int, `user-config`: Map[String, io.circe.Json])

  @JsonCodec(decodeOnly) case class JarsResponse(files: Option[List[JarFile]])

  @JsonCodec(decodeOnly) case class UploadJarResponse(filename: String)

  @JsonCodec(decodeOnly) case class JarFile(id: String, name: String)

  @JsonCodec(decodeOnly) case class ClusterOverview(`slots-total`: Int, `slots-available`: Int)

  @JsonCodec(decodeOnly) case class KeyValueEntry(key: String, value: String)

  @JsonCodec case class RunResponse(jobid: String)

  @JsonCodec case class FlinkError(errors: List[String])
}

