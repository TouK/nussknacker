package pl.touk.nussknacker.ui.process.test

import cats.data.EitherT
import cats.syntax.either._
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.{FetchLiveDataError, PerformTestError, SourceTestError}
import pl.touk.nussknacker.ui.process.test.TestInfoProvider.{
  ParametersDefinitionError,
  SourceTestDataGenerationError,
  TestingCapabilitiesError
}
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import java.time.Instant
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
  ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    testInfoProvider.getTestingCapabilities(processVersion, canonical)
  }

  def validateAndGetTestParametersDefinition(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit user: LoggedUser): Either[ParametersDefinitionError, Map[String, List[Parameter]]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    testInfoProvider
      .getTestParameters(processVersion, canonical)
  }

  def testUISourceParametersDefinition(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
  ): Either[ParametersDefinitionError, List[UISourceParameters]] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    testInfoProvider
      .getTestParameters(processVersion, canonical)
      .map(_.map { case (id, params) =>
        UISourceParameters(id, params.map(DefinitionsService.createUIParameter))
      }.toList)
  }

  def fetchSourcesLiveData(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      maxNumberOfSamples: Int
  )(
      implicit user: LoggedUser
  ): Either[FetchLiveDataError, RawScenarioTestData] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)

    for {
      _ <- validateSampleSize(maxNumberOfSamples)(FetchLiveDataError.TooManySamplesRequestedError)
      testData <- testInfoProvider
        .fetchSourcesLiveData(processVersion, canonical, maxNumberOfSamples)
        .leftMap(FetchLiveDataError.SourcesLiveDataFetchingError)
      rawTestData <- preliminaryScenarioTestDataSerDe
        .serialize(testData)
        .leftMap(FetchLiveDataError.ScenarioTestDataSerializationError)
    } yield rawTestData
  }

  def fetchSourceLiveData(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[SourceTestError, RawScenarioTestData] = {
    for {
      _ <- validateSampleSize(size)(SourceTestError.TooManySamplesRequestedError)
      result <- testInfoProvider
        .fetchSourceLiveData(metaData, sourceNodeData, size)
        .leftMap {
          case SourceTestDataGenerationError.SourceCompilationError(nodeId, errors) =>
            SourceTestError.SourceCompilationError(nodeId.id, errors.toList.map(_.toString))
          case SourceTestDataGenerationError.UnsupportedSourceError(nodeId) =>
            SourceTestError.UnsupportedSourcePreviewError(nodeId.id)
          case SourceTestDataGenerationError.NoLiveDataAvailable =>
            SourceTestError.NoLiveDataFetchedError
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
    } yield ResultsWithCounts(Instant.now(), testResults, computeCounts(canonical, isFragment, testResults))).value
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
    } yield ResultsWithCounts(Instant.now(), testResults, computeCounts(canonical, isFragment, testResults))).value
  }

  def resultsWithCounts(
      testResults: TestResults[Json],
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(implicit user: LoggedUser): ResultsWithCounts = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    ResultsWithCounts(Instant.now(), testResults, computeCounts(canonical, isFragment, testResults))
  }

  def validateSampleSize[E](size: Int)(tooManySamplesError: Int => E): Either[E, Unit] = {
    Either.cond(
      size <= testDataSettings.maxSamplesCount,
      (),
      tooManySamplesError(testDataSettings.maxSamplesCount)
    )
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
  sealed trait FetchLiveDataError

  object FetchLiveDataError {
    final case class SourcesLiveDataFetchingError(cause: TestInfoProvider.SourcesLiveDataFetchingError)
        extends FetchLiveDataError
    final case class ScenarioTestDataSerializationError(cause: PreliminaryScenarioTestDataSerDe.SerializationError)
        extends FetchLiveDataError
    final case class TooManySamplesRequestedError(maxSamples: Int) extends FetchLiveDataError
  }

  sealed trait SourceTestError

  object SourceTestError {
    final case class SourceCompilationError(nodeId: String, errors: List[String]) extends SourceTestError
    final case class UnsupportedSourcePreviewError(nodeId: String)                extends SourceTestError
    final case object NoLiveDataFetchedError                                      extends SourceTestError
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
