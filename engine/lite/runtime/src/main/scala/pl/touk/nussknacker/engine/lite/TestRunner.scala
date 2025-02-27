package pl.touk.nussknacker.engine.lite

import cats.{~>, Id, Monad}
import cats.data.Validated.{Invalid, Valid}
import io.circe.Json
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime
import pl.touk.nussknacker.engine.{ComponentUseCase, ModelData}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.language.higherKinds

trait TestRunner {

  def runTest(
      modelData: ModelData,
      jobData: JobData,
      scenarioTestData: ScenarioTestData,
      process: CanonicalProcess,
  ): TestResults[Json]

}

//TODO: integrate with Engine somehow?
class InterpreterTestRunner[F[_]: Monad: InterpreterShape: CapabilityTransformer: EffectUnwrapper, Input, Res <: AnyRef]
    extends TestRunner {

  def runTest(
      modelData: ModelData,
      jobData: JobData,
      scenarioTestData: ScenarioTestData,
      process: CanonicalProcess,
  ): TestResults[Json] = {

    // TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    ResultsCollectingListenerHolder.withTestEngineListener { collectingListener =>
      // in tests we don't send metrics anywhere
      val testContext                        = LiteEngineRuntimeContextPreparer.noOp.prepare(jobData)
      val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime
      val testServiceInvocationCollector     = new TestServiceInvocationCollector(collectingListener)

      // FIXME: validation??
      val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Input, Res](
        process,
        jobData,
        modelData,
        additionalListeners = List(collectingListener),
        testServiceInvocationCollector,
        componentUseCase
      )(
        implicitly[Monad[F]],
        SynchronousExecutionContextAndIORuntime.syncEc,
        implicitly[InterpreterShape[F]],
        implicitly[CapabilityTransformer[F]]
      ) match {
        case Valid(interpreter) => interpreter
        case Invalid(errors) =>
          throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
      }

      def getSourceById(sourceId: SourceId): Source = scenarioInterpreter.sources.getOrElse(
        sourceId,
        throw new IllegalArgumentException(
          s"Found source '${sourceId.value}' in a test record but is not present in the scenario"
        )
      )

      val testDataPreparer = TestDataPreparer(modelData, jobData)
      val inputs = ScenarioInputBatch(
        scenarioTestData.testRecords
          .groupBy(_.sourceId)
          .toList
          .flatMap { case (nodeId, scenarioTestRecords) =>
            val sourceId                     = SourceId(nodeId.id)
            val source                       = getSourceById(sourceId)
            val preparedRecords: List[Input] = testDataPreparer.prepareRecordsForTest(source, scenarioTestRecords)
            preparedRecords.map(record => sourceId -> record)
          }
      )

      try {
        scenarioInterpreter.open(testContext)

        val results = implicitly[EffectUnwrapper[F]].apply(scenarioInterpreter.invoke(inputs))

        collectSinkResults(testServiceInvocationCollector, results)
        collectingListener.results
      } finally {
        scenarioInterpreter.close()
        testContext.close()
      }
    }
  }

  private def collectSinkResults(
      testServiceInvocationCollector: TestServiceInvocationCollector,
      results: ResultType[EndResult[Res]]
  ): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      val node = result.nodeId.id
      testServiceInvocationCollector
        .createSinkInvocationCollector(node, node)
        .collect(result.context, result.result)
    }
  }

}

object TestRunner {

  type EffectUnwrapper[F[_]] = F ~> Id

  private val scenarioTimeout = 10 seconds

  // TODO: should we consider configurable timeout?
  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def apply[Y](eff: Future[Y]): Y = Await.result(eff, scenarioTimeout)
  }

}
