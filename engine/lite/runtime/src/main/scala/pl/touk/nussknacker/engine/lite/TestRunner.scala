package pl.touk.nussknacker.engine.lite

import cats.{~>, Id, Monad}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.{ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api.{JobData, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.{ScenarioTestCommonFormatJsonRecord, ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

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
    extends TestRunner
    with LazyLogging {

  def runTest(
      modelData: ModelData,
      jobData: JobData,
      scenarioTestData: ScenarioTestData,
      process: CanonicalProcess,
  ): TestResults[Json] = {

    // TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    ResultsCollectingListenerHolder.withTestEngineListener { collectingListener =>
      // in tests we don't send metrics anywhere
      val testContext                              = LiteEngineRuntimeContextPreparer.noOp.prepare(jobData)
      val componentUseContextProvider: RuntimeMode = RuntimeMode.Test
      val testServiceInvocationCollector           = new TestServiceInvocationCollector(collectingListener)

      // FIXME: validation??
      val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Input, Res](
        process,
        jobData,
        NodesDeploymentData.empty,
        modelData,
        additionalListeners = List(collectingListener),
        testServiceInvocationCollector,
        componentUseContextProvider
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
        scenarioTestData.inputRecords
          .groupBy(_.sourceId)
          .toList
          .flatMap { case (nodeId, scenarioTestRecords) =>
            val sourceId = SourceId(nodeId.value)
            val source   = getSourceById(sourceId)
            val recordsFromSourceSpecificTestDataFormat: List[Input] =
              testDataPreparer.prepareRecordsForTest(source, scenarioTestRecords)
            val recordsFromCommonTestDataFormat = decodeCommonFormatRecords(scenarioTestRecords, nodeId, source)
            (recordsFromSourceSpecificTestDataFormat ++ recordsFromCommonTestDataFormat)
              .map(record => sourceId -> record)
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

  // We currently have a limited support for "common" test data format for Lite engine. See inline comments for details
  private def decodeCommonFormatRecords(
      scenarioTestRecords: List[ScenarioTestRecord],
      sourceNodeId: NodeId,
      source: Source,
  ) = {
    val commonFormatRecords = scenarioTestRecords.zipWithIndex.collect {
      case (record: ScenarioTestCommonFormatJsonRecord, index) => (record, index)
    }
    if (commonFormatRecords.nonEmpty) {
      logger.warn(
        "Common format test records in input test data were found. Will be used experimental test records decoding mechanism"
      )
    }
    // For test data encoding we use Unknown for each provided variable name because at this stage we don't have source output typing here.
    val variableNames = commonFormatRecords.flatMap { case (record, _) => record.variables.keys }.toSet
    val validationContext = variableNames.foldLeft(ValidationContext.empty) { case (ctx, variableName) =>
      ctx.withVariableUnsafe(variableName, Unknown)
    }
    val decoder = new CommonTestDataFormatVariablesDecoder(validationContext, sourceNodeId)
    commonFormatRecords.map { case (record, testRecordIndex) =>
      val decodedVariables = decoder.decode(record.variables, testRecordIndex)
      val value =
        if (decodedVariables.keySet == Set(VariableConstants.InputVariableName)) {
          decodedVariables(VariableConstants.InputVariableName)
        } else {
          decodedVariables
        }
      transformToEngineSpecificInputRecord(value, source)
    }
  }

  protected def transformToEngineSpecificInputRecord(testRecord: Any, source: Source): Input =
    transformToEngineSpecificInputRecord(testRecord)

  // FIXME: it doesn't work properly with KafkaLite Engine, we should add proper converting method
  protected def transformToEngineSpecificInputRecord(testRecord: Any): Input = {
    testRecord.asInstanceOf[Input]
  }

  private def collectSinkResults(
      testServiceInvocationCollector: TestServiceInvocationCollector,
      results: ResultType[EndResult[Res]]
  ): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      testServiceInvocationCollector
        .createSinkInvocationCollector(result.nodeId, result.nodeId.value)
        .foreach(_.collect(result.context, result.result))
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
