package pl.touk.nussknacker.engine.lite

import cats.{Id, ~>}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.{ProcessName, RunMode, Source}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.language.higherKinds


//TODO: integrate with Engine somehow?
//Base test runner, creating Context from sampleData and mapping results are left for the implementations for now
abstract class TestRunner[F[_] : InterpreterShape : CapabilityTransformer : EffectUnwrapper, Input, Res <: AnyRef] {

  def sampleToSource(sampleData: List[AnyRef], sources: Map[SourceId, Source]): ScenarioInputBatch[Input]

  def runTest[T](modelData: ModelData,
                 testData: TestData,
                 process: EspProcess,
                 variableEncoder: Any => T): TestResults[T] = {

    //TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    val parsedTestData = new TestDataPreparer(modelData).prepareDataForTest(process, testData)

    //in tests we don't send metrics anywhere
    val testContext = LiteEngineRuntimeContextPreparer.noOp.prepare(testJobData(process))
    val runMode: RunMode = RunMode.Test

    //FIXME: validation??
    val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Input, Res](process, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), runMode
    )(SynchronousExecutionContext.ctx, implicitly[InterpreterShape[F]], implicitly[CapabilityTransformer[F]]) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    try {
      scenarioInterpreter.open(testContext)

      val inputs = sampleToSource(parsedTestData.samples, scenarioInterpreter.sources)

      val results = implicitly[EffectUnwrapper[F]].apply(scenarioInterpreter.invoke(inputs))

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

object TestRunner {

  type EffectUnwrapper[F[_]] = F ~> Id

}
