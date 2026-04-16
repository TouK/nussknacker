package pl.touk.nussknacker.engine.management.jobrunner

import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration.{Configuration, PipelineOptionsInternal}
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.DeploymentIdOps
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class FlinkMiniClusterScenarioJobRunner(
    miniClusterWithServices: FlinkMiniClusterWithServices,
    modelDataProvider: BaseModelDataProvider
)(implicit executionContext: ExecutionContext)
    extends FlinkScenarioJobRunner {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[JobExecutionResult](
    modelDataProvider.modelClassLoader,
    "pl.touk.nussknacker.engine.process.runner.FlinkScenarioJob",
    "run"
  )

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[JobID]] = {
    Future {
      miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
        val conf = new Configuration()
        savepointPathOpt.foreach { savepointPath =>
          SavepointRestoreSettings.toConfiguration(SavepointRestoreSettings.forPath(savepointPath, true), conf)
        }
        command.deploymentData.deploymentId.toNewDeploymentIdOpt.map(_.toJobID).foreach { jobId =>
          conf.set(PipelineOptionsInternal.PIPELINE_FIXED_JOB_ID, jobId.toHexString)
        }
        env.configure(conf)
        val jobID = jobInvoker
          .invokeStaticMethod(
            modelDataProvider.getCurrentModelData(),
            command.canonicalProcess.withoutUnsafeFields,
            command.processVersion,
            command.deploymentData,
            env,
            List.empty,                                    // processListeners
            Some("MiniCluster is used for the deployment") // skipLiveDataUploaderWithReason
          )
          .getJobID
        Some(jobID)
      }
    }
  }

}
