package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.Json
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioParallelismOverride._
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster.LegacyFallbackToSingleUseMiniClusterHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}

class FlinkMiniClusterScenarioTestRunner(
    modelData: BaseModelData,
    sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
    parallelism: Int,
    waitForJobIsFinishedRetryPolicy: retry.Policy
)(implicit executionContext: ExecutionContext, ioRuntime: IORuntime) {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[JobExecutionResult](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkScenarioTestingJob",
    "run"
  )

  private val legacyFallbackToSingleUseMiniClusterHandler =
    new LegacyFallbackToSingleUseMiniClusterHandler(modelData.modelClassLoader, "scenario testing")

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def runTests(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): Future[TestResults[Json]] = {
    legacyFallbackToSingleUseMiniClusterHandler.withSharedOrSingleUseClusterAsync(
      sharedMiniClusterServicesOpt,
      scenario
    ) { miniClusterWithServices =>
      val scenarioWithOverriddenParallelism = sharedMiniClusterServicesOpt
        .map(_ => scenario.overrideParallelism(parallelism))
        .getOrElse(scenario)
      def runJob(
          collectingListener: ResultsCollectingListener[Json],
          env: StreamExecutionEnvironment
      ): JobExecutionResult = {
        jobInvoker.invokeStaticMethod(
          modelData,
          scenarioWithOverriddenParallelism,
          scenarioTestData,
          collectingListener,
          env
        )
      }
      (for {
        env                <- miniClusterWithServices.createDetachedStreamExecutionEnvironment
        collectingListener <- ResultsCollectingListenerHolder.registerTestEngineListener
      } yield (env, collectingListener))
        .use { case (env, collectingListener) =>
          (for {
            executionResult <- EitherT.right(IO(runJob(collectingListener, env)))
            _ <- EitherT(IO.fromFuture(IO {
              miniClusterWithServices.waitForJobIsFinished(executionResult.getJobID)(
                waitForJobIsFinishedRetryPolicy,
                // We don't want to extend request processing time
                terminalCheckRetryPolicyOpt = None
              )
            }))
          } yield collectingListener.results).value
        }
        .unsafeToFuture()
        // TODO: business error returned to endpoint
        .map(_.toTry.get)
    }
  }

}
