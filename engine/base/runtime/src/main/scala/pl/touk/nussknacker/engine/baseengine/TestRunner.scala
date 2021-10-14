package pl.touk.nussknacker.engine.baseengine

import cats.MonadError
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.{ProcessName, RunMode, Source}
import pl.touk.nussknacker.engine.api.{Context, JobData, ProcessVersion}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{EndResult, ResultType, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.RuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.testmode._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds


//TODO: integrate with Engine somehow?
//Base test runner, creating Context from sampleData and mapping results are left for the implementations for now
abstract class TestRunner[F[_], Res <: AnyRef](engine: BaseScenarioEngine[F, Res])(implicit shape: InterpreterShape[F]) {

  def sampleToSource(sampleData: List[AnyRef], sources: Map[SourceId, Source[Any]]): List[(SourceId, Context)]

  def getResults(results: F[ResultType[EndResult[Res]]]): ResultType[EndResult[Res]]

  implicit val monad: MonadError[F, Throwable] = shape.monadError

  def runTest[T](modelData: ModelData,
                 testData: TestData,
                 process: EspProcess,
                 variableEncoder: Any => T): TestResults[T] = {

    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    val parsedTestData = new TestDataPreparer(modelData).prepareDataForTest(process, testData)

    //in tests we don't send metrics anywhere
    val testContext = new RuntimeContextPreparer(NoOpMetricsProvider)
    val runMode: RunMode = RunMode.Test

    //FIXME: validation??
    val standaloneInterpreter = engine.createInterpreter(process, testContext, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), runMode
    ) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    try {
      // testing process may be unreleased, so it has no version
      val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version"))
      val deploymentData = DeploymentData.empty
      standaloneInterpreter.open(JobData(process.metaData, processVersion, deploymentData))

      val inputs = sampleToSource(parsedTestData.samples, standaloneInterpreter.sources)

      val results = getResults(standaloneInterpreter.invoke(inputs))

      collectSinkResults(collectingListener.runId, results)
      collectExceptions(collectingListener, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
      standaloneInterpreter.close()
    }

  }

  private def collectSinkResults(runId: TestRunId, results: ResultType[EndResult[Res]]): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      val node = result.nodeId
      SinkInvocationCollector(runId, node, node).collect(result.context, result.result)
    }
  }

  private def collectExceptions(listener: ResultsCollectingListener, results: ResultType[EndResult[Res]]): Unit = {
    val exceptions = results.written
    exceptions.foreach(listener.exceptionThrown)
  }


}