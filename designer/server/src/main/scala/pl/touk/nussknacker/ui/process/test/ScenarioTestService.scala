package pl.touk.nussknacker.ui.process.test

import cats.data.EitherT
import cats.syntax.either._
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.SourceTestDataGenerationError
import pl.touk.nussknacker.engine.definition.test.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.{
  GenerateTestDataError,
  PerformTestError,
  SourceTestError
}
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestService(
    testInfoProvider: TestInfoProvider,
    processResolver: UIProcessResolver,
    testDataSettings: TestDataSettings,
    preliminaryScenarioTestDataSerDe: PreliminaryScenarioTestDataSerDe,
    processCounter: ProcessCounter,
    testExecutorService: ScenarioTestExecutorService,
) extends LazyLogging {

  def getTestingCapabilities(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
  ): TestingCapabilities = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    testInfoProvider.getTestingCapabilities(processVersion, canonical)
  }

  def validateAndGetTestParametersDefinition(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit user: LoggedUser): Map[String, List[Parameter]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    testInfoProvider
      .getTestParameters(processVersion, canonical)
  }

  def testUISourceParametersDefinition(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
  ): List[UISourceParameters] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    testInfoProvider
      .getTestParameters(processVersion, canonical)
      .map { case (id, params) => UISourceParameters(id, params.map(DefinitionsService.createUIParameter)) }
      .map {
        assignUserFriendlyEditor
      }
      .toList
  }

  def generateData(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      testSampleSize: Int
  )(
      implicit user: LoggedUser
  ): Either[GenerateTestDataError, RawScenarioTestData] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)

    for {
      _ <- validateSampleSize(testSampleSize)(GenerateTestDataError.TooManySamplesRequestedError)
      generatedData <- testInfoProvider
        .generateTestData(processVersion, canonical, testSampleSize)
        .leftMap(GenerateTestDataError.ScenarioTestDataGenerationError)
      rawTestData <- preliminaryScenarioTestDataSerDe
        .serialize(generatedData)
        .leftMap(GenerateTestDataError.ScenarioTestDataSerializationError)
    } yield rawTestData
  }

  def getDataFromSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[SourceTestError, RawScenarioTestData] = {
    for {
      _ <- validateSampleSize(size)(SourceTestError.TooManySamplesRequestedError)
      result <- testInfoProvider
        .generateTestDataForSource(metaData, sourceNodeData, size)
        .leftMap {
          case SourceTestDataGenerationError.SourceCompilationError(nodeId, errors) =>
            SourceTestError.SourceCompilationError(nodeId, errors.map(_.toString))
          case SourceTestDataGenerationError.UnsupportedSourceError(nodeId) =>
            SourceTestError.UnsupportedSourcePreviewError(nodeId)
          case SourceTestDataGenerationError.NoDataGenerated =>
            SourceTestError.NoDataGeneratedError
        }
      rawTestData <- preliminaryScenarioTestDataSerDe
        .serialize(result)
        .leftMap(serializationError => SourceTestError.ScenarioTestDataSerializationError(serializationError))
    } yield rawTestData
  }

  def performTest(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      rawTestData: RawScenarioTestData,
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    (for {
      preliminaryScenarioTestData <- EitherT.fromEither[Future](
        preliminaryScenarioTestDataSerDe
          .deserialize(rawTestData)
          .leftMap[PerformTestError](PerformTestError.DeserializationError)
      )
      canonical = toCanonicalProcess(
        scenarioGraph,
        processVersion,
        isFragment
      )
      scenarioTestData <- EitherT.fromEither[Future](
        testInfoProvider
          .prepareTestData(preliminaryScenarioTestData, canonical)
          .leftMap[PerformTestError](PerformTestError.TestDataPreparationError)
      )
      testResults <- EitherT.liftF(
        testExecutorService.testProcess(
          processVersion,
          canonical,
          scenarioTestData,
        )
      )
      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
    } yield ResultsWithCounts(testResults, computeCounts(canonical, isFragment, testResults))).value
  }

  def performTest(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      parameterTestData: TestSourceParameters,
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    (for {
      testResults <- EitherT.liftF(
        testExecutorService.testProcess(
          processVersion,
          canonical,
          ScenarioTestData(parameterTestData.sourceId, parameterTestData.parameterExpressions),
        )
      )
      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
    } yield ResultsWithCounts(testResults, computeCounts(canonical, isFragment, testResults))).value
  }

  private def validateSampleSize[E](size: Int)(tooManySamplesError: Int => E): Either[E, Unit] = {
    Either.cond(
      size <= testDataSettings.maxSamplesCount,
      (),
      tooManySamplesError(testDataSettings.maxSamplesCount)
    )
  }

  private def assignUserFriendlyEditor(uiSourceParameter: UISourceParameters): UISourceParameters = {
    val adaptedParameters = uiSourceParameter.parameters.map { uiParameter =>
      uiParameter.editor match {
        case DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)
            if uiParameter.typ.canBeConvertedTo(Typed[String]) =>
          uiParameter.copy(editor = StringParameterEditor)
        case _ => uiParameter
      }
    }
    uiSourceParameter.copy(parameters = adaptedParameters)
  }

  private def toCanonicalProcess(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(implicit user: LoggedUser): CanonicalProcess = {
    processResolver.validateAndResolve(
      scenarioGraph,
      processVersion,
      isFragment,
    )
  }

  private def validateTestResultsAreNotTooBig(testResults: TestResults[_]): Either[PerformTestError, Unit] = {
    val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
    Either.cond(
      testDataResultApproxByteSize <= testDataSettings.resultsMaxBytes,
      (),
      PerformTestError.TestResultsSizeExceeded(testDataResultApproxByteSize, testDataSettings.resultsMaxBytes)
    )
  }

  private def computeCounts(canonical: CanonicalProcess, isFragment: Boolean, results: TestResults[_])(
      implicit loggedUser: LoggedUser
  ): Map[String, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(
        nresults.size.toLong,
        results.exceptions.find(_.nodeId.contains(key)).size.toLong
      )
    }
    processCounter.computeCounts(canonical, isFragment, counts.get)
  }

}

object ScenarioTestService {
  sealed trait GenerateTestDataError

  object GenerateTestDataError {
    final case class ScenarioTestDataGenerationError(cause: TestInfoProvider.ScenarioTestDataGenerationError)
        extends GenerateTestDataError
    final case class ScenarioTestDataSerializationError(cause: PreliminaryScenarioTestDataSerDe.SerializationError)
        extends GenerateTestDataError
    final case class TooManySamplesRequestedError(maxSamples: Int) extends GenerateTestDataError
  }

  sealed trait SourceTestError

  object SourceTestError {
    final case class SourceCompilationError(nodeId: String, errors: List[String]) extends SourceTestError
    final case class UnsupportedSourcePreviewError(nodeId: String)                extends SourceTestError
    final case object NoDataGeneratedError                                        extends SourceTestError
    final case class ScenarioTestDataSerializationError(cause: PreliminaryScenarioTestDataSerDe.SerializationError)
        extends SourceTestError
    final case class TooManySamplesRequestedError(maxSamples: Int) extends SourceTestError
  }

  sealed trait PerformTestError

  object PerformTestError {
    final case class DeserializationError(cause: PreliminaryScenarioTestDataSerDe.DeserializationError)
        extends PerformTestError
    final case class TestDataPreparationError(cause: TestInfoProvider.TestDataPreparationError) extends PerformTestError
    final case class TestResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long)           extends PerformTestError
  }

}
