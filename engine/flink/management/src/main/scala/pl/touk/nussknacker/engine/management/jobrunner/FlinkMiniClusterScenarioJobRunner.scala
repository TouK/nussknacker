package pl.touk.nussknacker.engine.management.jobrunner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.deployment.DMRunDeploymentCommand
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}

class FlinkMiniClusterScenarioJobRunner(
    miniClusterWithServices: FlinkMiniClusterWithServices,
    modelData: BaseModelData
)(implicit executionContext: ExecutionContext)
    extends FlinkScenarioJobRunner {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[JobExecutionResult](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.runner.FlinkScenarioJob",
    "run"
  )

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    Future {
      miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
        savepointPathOpt.foreach { savepointPath =>
          val conf = new Configuration()
          SavepointRestoreSettings.toConfiguration(SavepointRestoreSettings.forPath(savepointPath, true), conf)
          env.configure(conf)
        }
        val jobID = jobInvoker
          .invokeStaticMethod(
            modelData,
            command.canonicalProcess,
            command.processVersion,
            command.deploymentData,
            env
          )
          .getJobID
        Some(ExternalDeploymentId(jobID.toHexString))
      }
    }
  }

}
