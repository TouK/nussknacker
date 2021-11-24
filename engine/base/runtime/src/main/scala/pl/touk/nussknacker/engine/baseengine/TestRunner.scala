package pl.touk.nussknacker.engine.baseengine

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.{ProcessName, RunMode, Source}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.language.higherKinds


//TODO: integrate with Engine somehow?
//Base test runner, creating Context from sampleData and mapping results are left for the implementations for now
abstract class TestRunner[F[_], Res <: AnyRef](shape: InterpreterShape[F], capabilityTransformer: CapabilityTransformer[F]) {

  def sampleToSource(sampleData: List[AnyRef], sources: Map[SourceId, Source]): ScenarioInputBatch

  def getResults(results: F[ResultType[EndResult[Res]]]): ResultType[EndResult[Res]]

  def runTest[T](modelData: ModelData,
                 testData: TestData,
                 process: EspProcess,
                 variableEncoder: Any => T): TestResults[T] = {

    //TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    val parsedTestData = new TestDataPreparer(modelData).prepareDataForTest(process, testData)

    //in tests we don't send metrics anywhere
    val testContext = EngineRuntimeContextPreparer.noOp.prepare(testJobData(process))
    val runMode: RunMode = RunMode.Test

    //FIXME: validation??
    val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Res](process, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), runMode
    )(SynchronousExecutionContext.ctx, shape, capabilityTransformer) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    try {
      scenarioInterpreter.open(testContext)

      val inputs = sampleToSource(parsedTestData.samples, scenarioInterpreter.sources)

      val results = getResults(scenarioInterpreter.invoke(inputs))

      collectSinkResults(collectingListener.runId, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
      scenarioInterpreter.close()
      testContext.close()
    }

  }

  private def testJobData(process: EspProcess) = {
    // testing process may be unreleased, so it has no version
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version"))
    val deploymentData = DeploymentData.empty
    JobData(process.metaData, processVersion, deploymentData)
  }

  private def collectSinkResults(runId: TestRunId, results: ResultType[EndResult[Res]]): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      val node = result.nodeId.id
      SinkInvocationCollector(runId, node, node).collect(result.context, result.result)
    }
  }

}