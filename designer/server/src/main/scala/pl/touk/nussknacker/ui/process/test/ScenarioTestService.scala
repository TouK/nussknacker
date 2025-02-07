package pl.touk.nussknacker.ui.process.test

import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.TestDataGenerationError
import pl.touk.nussknacker.engine.definition.test.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.ScenarioTestError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.ScenarioTestError.TooManySamplesRequestedError
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
  ): Either[String, RawScenarioTestData] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)

    for {
      _ <- validateSampleSize(testSampleSize).left.map(error =>
        s"Too many samples requested, limit is ${error.maxSamples}"
      )
      generatedData <- testInfoProvider.generateTestData(processVersion, canonical, testSampleSize).left.map(_.message)
      rawTestData   <- preliminaryScenarioTestDataSerDe.serialize(generatedData)
    } yield rawTestData
  }

  def getDataFromSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[ScenarioTestError, RawScenarioTestData] = {
    for {
      _ <- validateSampleSize(size)
      result <- testInfoProvider
        .generateTestDataForSource(metaData, sourceNodeData, size)
        .left
        .map {
          case TestDataGenerationError.SourceCompilationError(nodeId, errors) =>
            ScenarioTestError.SourceCompilationError(nodeId, errors.map(_.toString))
          case TestDataGenerationError.UnsupportedSourceError(nodeId) =>
            ScenarioTestError.UnsupportedSourcePreviewError(nodeId)
          case TestDataGenerationError.NoDataGenerated =>
            ScenarioTestError.NoDataGeneratedError
          case TestDataGenerationError.NoSourcesWithTestDataGeneration =>
            ScenarioTestError.NoSourcesWithTestDataGenerationError
        }
      rawTestData <- preliminaryScenarioTestDataSerDe
        .serialize(result)
        .left
        .map(msg => ScenarioTestError.SerializationError(s"Failed to serialize test data: $msg"))
    } yield rawTestData
  }

  def performTest(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      rawTestData: RawScenarioTestData,
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    for {
      preliminaryScenarioTestData <- preliminaryScenarioTestDataSerDe
        .deserialize(rawTestData)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      canonical = toCanonicalProcess(
        scenarioGraph,
        processVersion,
        isFragment
      )
      scenarioTestData <- testInfoProvider
        .prepareTestData(preliminaryScenarioTestData, canonical)
        // TODO: handle error from prepareTestData in better way
        .fold(error => Future.failed(new IllegalArgumentException(error.message)), Future.successful)
      testResults <- testExecutorService.testProcess(
        processVersion,
        canonical,
        scenarioTestData,
      )
      _ <- {
        assertTestResultsAreNotTooBig(testResults)
      }
    } yield ResultsWithCounts(testResults, computeCounts(canonical, isFragment, testResults))
  }

  def performTest(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      parameterTestData: TestSourceParameters,
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    for {
      testResults <- testExecutorService.testProcess(
        processVersion,
        canonical,
        ScenarioTestData(parameterTestData.sourceId, parameterTestData.parameterExpressions),
      )
      _ <- assertTestResultsAreNotTooBig(testResults)
    } yield ResultsWithCounts(testResults, computeCounts(canonical, isFragment, testResults))
  }

  private def validateSampleSize(size: Int): Either[TooManySamplesRequestedError, Unit] = {
    Either.cond(
      size <= testDataSettings.maxSamplesCount,
      (),
      ScenarioTestError.TooManySamplesRequestedError(testDataSettings.maxSamplesCount)
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

  private def assertTestResultsAreNotTooBig(testResults: TestResults[_]): Future[Unit] = {
    val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
    if (testDataResultApproxByteSize > testDataSettings.resultsMaxBytes) {
      logger.info(
        s"Test data limit exceeded. Approximate test data size: $testDataResultApproxByteSize, but limit is: ${testDataSettings.resultsMaxBytes}"
      )
      Future.failed(new RuntimeException("Too much test data. Please decrease test input data size."))
    } else {
      Future.successful(())
    }
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
  sealed trait ScenarioTestError

  object ScenarioTestError {
    final case class SourceCompilationError(nodeId: String, errors: List[String]) extends ScenarioTestError
    final case class UnsupportedSourcePreviewError(nodeId: String)                extends ScenarioTestError
    case object NoDataGeneratedError                                              extends ScenarioTestError
    case object NoSourcesWithTestDataGenerationError                              extends ScenarioTestError
    final case class SerializationError(message: String)                          extends ScenarioTestError
    final case class TooManySamplesRequestedError(maxSamples: Int)                extends ScenarioTestError
  }

}
