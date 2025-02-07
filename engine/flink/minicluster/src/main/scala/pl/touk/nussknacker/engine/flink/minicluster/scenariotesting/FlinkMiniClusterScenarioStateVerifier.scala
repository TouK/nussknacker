package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioParallelismOverride.Ops
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster.LegacyFallbackToSingleUseMiniClusterHandler
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}

class FlinkMiniClusterScenarioStateVerifier(
    modelData: BaseModelData,
    sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
    waitForJobIsFinishedRetryPolicy: retry.Policy
)(implicit executionContext: ExecutionContext, ioRuntime: IORuntime)
    extends LazyLogging {

  private val StateVerificationParallelism = 1

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[JobExecutionResult](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkScenarioStateVerificationJob",
    "run"
  )

  private val legacyFallbackToSingleUseMiniClusterHandler =
    new LegacyFallbackToSingleUseMiniClusterHandler(modelData.modelClassLoader, "scenario state verification")

  def verify(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      savepointPath: String
  ): Future[Unit] = {
    legacyFallbackToSingleUseMiniClusterHandler.withSharedOrSingleUseCluster(sharedMiniClusterServicesOpt, scenario) {
      miniClusterWithServices =>
        val scenarioWithOverriddenParallelism = sharedMiniClusterServicesOpt
          .map(_ => scenario.overrideParallelism(StateVerificationParallelism))
          .getOrElse(scenario)
        def runJob(env: StreamExecutionEnvironment): JobExecutionResult =
          jobInvoker.invokeStaticMethod(
            modelData,
            scenarioWithOverriddenParallelism,
            processVersion,
            savepointPath,
            env
          )
        val scenarioName = processVersion.processName
        miniClusterWithServices.createDetachedStreamExecutionEnvironment
          .use { env =>
            logger.info(s"Starting to verify $scenarioName")
            (for {
              executionResult <- EitherT.right(IO(runJob(env)))
              _ <- EitherT(IO.fromFuture(IO {
                miniClusterWithServices.waitForJobIsFinished(executionResult.getJobID)(
                  waitForJobIsFinishedRetryPolicy,
                  // We don't want to extend request processing time
                  terminalCheckRetryPolicyOpt = None
                )
              }))
            } yield ()).value
          }
          .unsafeToFuture()
          // TODO: business error returned to endpoint
          .map {
            case Right(_) =>
              logger.info(s"Verification of $scenarioName successful")
            case Left(jobStateCheckError) =>
              logger.info(s"Failed to verify $scenarioName", jobStateCheckError)
              throw new IllegalArgumentException(
                "State is incompatible, please stop scenario and start again with clean state",
                jobStateCheckError
              )
          }
    }
  }

}
