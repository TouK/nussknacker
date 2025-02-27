package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.flink.api.common.JobID
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.management.rest.{flinkRestModel, FlinkClient}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{
  ClusterOverview,
  ExecutionConfig,
  JobDetails,
  JobOverview
}

import java.io.File
import scala.concurrent.Future

object FlinkClientStub extends FlinkClient {

  val maxParallelism    = 10
  val savepointPath     = "savepoints/123-savepoint"
  val stopSavepointPath = "savepoints/246-stop-savepoint"

  override def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[JobOverview]]] =
    Future.successful(WithDataFreshnessStatus.fresh(List.empty))

  override def getJobDetails(jobId: JobID): Future[Option[JobDetails]] = Future.successful(None)

  override def getJobConfig(jobId: JobID): Future[ExecutionConfig] =
    Future.successful(ExecutionConfig(1, Map.empty))

  override def cancel(jobId: JobID): Future[Unit] = Future.successful(())

  override def makeSavepoint(
      jobId: JobID,
      savepointDir: Option[String]
  ): Future[SavepointResult] = Future.successful(SavepointResult(savepointPath))

  override def stop(jobId: JobID, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(stopSavepointPath))

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[JobID]
  ): Future[Option[JobID]] = Future.successful(None)

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = Future.successful(())

  override def getClusterOverview: Future[ClusterOverview] =
    Future.successful(
      ClusterOverview(`slots-total` = maxParallelism, `slots-available` = maxParallelism)
    )

  override def getJobManagerConfig: Future[Configuration] = Future.successful(new Configuration)

}
