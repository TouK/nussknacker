package pl.touk.nussknacker.engine.management.rest

import io.circe.generic.JsonCodec
import org.apache.flink.api.common

object flinkRestModel {

  /*
  When #programArgsList is not set in request Flink warns that
  <pre>Configuring the job submission via query parameters is deprecated. Please migrate to submitting a JSON request instead.</pre>
  But now we can't add #programArgsList support because of back compatibility of Flink 1.6..
   */
  @JsonCodec(encodeOnly = true) case class DeployProcessRequest(entryClass: String,
                                                                savepointPath: Option[String],
                                                                programArgs: String,
                                                                parallelism: Int = common.ExecutionConfig.PARALLELISM_DEFAULT,
                                                                allowNonRestoredState: Boolean = true)

  @JsonCodec(encodeOnly = true) case class SavepointTriggerRequest(`target-directory`: Option[String], `cancel-job`: Boolean)

  @JsonCodec(encodeOnly = true) case class StopRequest(targetDirectory: Option[String], drain: Boolean)

  @JsonCodec(decodeOnly = true) case class SavepointTriggerResponse(`request-id`: String)

  @JsonCodec(decodeOnly = true) case class GetSavepointStatusResponse(status: SavepointStatus, operation: Option[SavepointOperation]) {

    def isCompletedSuccessfully: Boolean = status.isCompleted && operation.flatMap(_.location).isDefined

    def isFailed: Boolean = status.isCompleted && !isCompletedSuccessfully

  }

  @JsonCodec(decodeOnly = true) case class SavepointOperation(location: Option[String], `failure-cause`: Option[FailureCause])

  @JsonCodec(decodeOnly = true) case class FailureCause(`class`: Option[String], `stack-trace`: Option[String], `serialized-throwable`: Option[String])

  @JsonCodec(decodeOnly = true) case class SavepointStatus(id: String) {
    def isCompleted: Boolean = id == "COMPLETED"
  }

  @JsonCodec(decodeOnly = true) case class JobsResponse(jobs: List[JobOverview])

  //NOTE: Flink <1.10 compatibility - JobStatus changed package, so we use String here
  @JsonCodec(decodeOnly = true) case class JobOverview(jid: String, name: String, `last-modification`: Long, `start-time`: Long, state: String)

  @JsonCodec(decodeOnly = true) case class JobConfig(jid: String, `execution-config`: ExecutionConfig)

  @JsonCodec(decodeOnly = true) case class ExecutionConfig(`user-config`: Map[String, io.circe.Json])

  @JsonCodec(decodeOnly = true) case class JarsResponse(files: Option[List[JarFile]])

  @JsonCodec(decodeOnly = true) case class UploadJarResponse(filename: String)

  @JsonCodec(decodeOnly = true) case class JarFile(id: String, name: String)

  @JsonCodec case class RunResponse(jobid: String)
}

