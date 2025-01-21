package pl.touk.nussknacker.engine.management.testsmechanism

import io.circe.Json
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.{MetaDataExtractor, ReflectiveMethodInvoker}

import scala.concurrent.{ExecutionContext, Future}

class FlinkProcessTestRunner(modelData: ModelData, parallelism: Int, streamExecutionConfig: Configuration)
    extends AutoCloseable {

  private val streamExecutionEnvironment =
    TestsMechanismStreamExecutionEnvironmentFactory.createStreamExecutionEnvironment(parallelism, streamExecutionConfig)

  private val miniCluster = TestsMechanismMiniClusterFactory.createConfiguredMiniCluster(parallelism)

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside flinkDeploymentManager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which Flink DM is loaded
  private val methodInvoker = new ReflectiveMethodInvoker[TestResults[Json]](
    modelData.modelClassLoader.classLoader,
    "pl.touk.nussknacker.engine.process.runner.FlinkTestMain",
    "run"
  )

  def runTestsAsync(canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData)(
      implicit ec: ExecutionContext
  ): Future[TestResults[Json]] =
    Future {
      runTests(canonicalProcess, scenarioTestData)
    }

  // NU-1455: We encode variable on the engine, because of classLoader's problems
  def runTests(canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] =
    methodInvoker.invokeStaticMethod(
      miniCluster,
      streamExecutionEnvironment,
      modelData,
      rewriteParallelismIfHigherThanMaxParallelism(canonicalProcess),
      scenarioTestData
    )

  private def rewriteParallelismIfHigherThanMaxParallelism(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](canonicalProcess.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    if (scenarioParallelism > parallelism) {
      canonicalProcess.copy(metaData =
        canonicalProcess.metaData.copy(additionalFields =
          canonicalProcess.metaData.additionalFields.copy(properties =
            canonicalProcess.metaData.additionalFields.properties + (StreamMetaData.parallelismName -> parallelism.toString)
          )
        )
      )
    } else {
      canonicalProcess
    }
  }

  def close(): Unit = {
    miniCluster.close()
    streamExecutionEnvironment.close()
  }

}
