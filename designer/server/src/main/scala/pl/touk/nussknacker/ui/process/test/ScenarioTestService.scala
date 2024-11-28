package pl.touk.nussknacker.ui.process.test

import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.AssignabilityDeterminer
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
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
      isFragment: Boolean,
  )(
      implicit user: LoggedUser
  ): TestingCapabilities = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    testInfoProvider.getTestingCapabilities(processVersion, canonical)
  }

  def testParametersDefinition(
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
      isFragment: Boolean
  )(implicit user: LoggedUser): List[UISourceParameters] =
    testParametersDefinition(scenarioGraph, processVersion, isFragment)
      .map { case (id, params) => UISourceParameters(id, params.map(DefinitionsService.createUIParameter)) }
      .map { assignUserFriendlyEditor }
      .toList

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
      _ <- Either.cond(
        testSampleSize <= testDataSettings.maxSamplesCount,
        (),
        s"Too many samples requested, limit is ${testDataSettings.maxSamplesCount}"
      )
      generatedData <- testInfoProvider.generateTestData(processVersion, canonical, testSampleSize)
      rawTestData   <- preliminaryScenarioTestDataSerDe.serialize(generatedData)
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
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
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
