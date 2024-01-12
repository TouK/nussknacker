package pl.touk.nussknacker.ui.process.test

import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.TestSourceParameters
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestService(
    testInfoProviders: ProcessingTypeDataProvider[TestInfoProvider, _],
    testDataSettings: TestDataSettings,
    preliminaryScenarioTestDataSerDe: PreliminaryScenarioTestDataSerDe,
    processResolvers: ProcessingTypeDataProvider[UIProcessResolver, _],
    processCounter: ProcessCounter,
    testExecutorService: ScenarioTestExecutorService,
) extends LazyLogging {

  def getTestingCapabilities(displayableProcess: DisplayableProcess)(implicit user: LoggedUser): TestingCapabilities = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    val canonical        = toCanonicalProcess(displayableProcess)
    testInfoProvider.getTestingCapabilities(canonical)
  }

  def testParametersDefinition(
      displayableProcess: DisplayableProcess
  )(implicit user: LoggedUser): List[UISourceParameters] = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    val canonical        = toCanonicalProcess(displayableProcess)
    testInfoProvider
      .getTestParameters(canonical)
      .map { case (id, params) => UISourceParameters(id, params.map(DefinitionsService.createUIParameter)) }
      .toList
  }

  def generateData(displayableProcess: DisplayableProcess, testSampleSize: Int)(
      implicit user: LoggedUser
  ): Either[String, RawScenarioTestData] = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    val canonical        = toCanonicalProcess(displayableProcess)

    for {
      _ <- Either.cond(
        testSampleSize <= testDataSettings.maxSamplesCount,
        (),
        s"Too many samples requested, limit is ${testDataSettings.maxSamplesCount}"
      )
      generatedData <- testInfoProvider
        .generateTestData(canonical, testSampleSize)
        .toRight("Test data could not be generated for scenario")
      rawTestData <- preliminaryScenarioTestDataSerDe.serialize(generatedData)
    } yield rawTestData
  }

  def performTest[T](
      idWithName: ProcessIdWithName,
      displayableProcess: DisplayableProcess,
      rawTestData: RawScenarioTestData
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    for {
      preliminaryScenarioTestData <- preliminaryScenarioTestDataSerDe
        .deserialize(rawTestData)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      canonical = toCanonicalProcess(displayableProcess)
      scenarioTestData <- testInfoProvider
        .prepareTestData(preliminaryScenarioTestData, canonical)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      testResults <- testExecutorService.testProcess(
        idWithName,
        canonical,
        displayableProcess.processingType,
        scenarioTestData
      )
      _ <- {
        assertTestResultsAreNotTooBig(testResults)
      }
    } yield ResultsWithCounts(testResults, computeCounts(canonical, testResults))
  }

  def performTest[T](
      idWithName: ProcessIdWithName,
      displayableProcess: DisplayableProcess,
      parameterTestData: TestSourceParameters
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    val canonical = toCanonicalProcess(displayableProcess)
    for {
      testResults <- testExecutorService.testProcess(
        idWithName,
        canonical,
        displayableProcess.processingType,
        ScenarioTestData(parameterTestData.sourceId, parameterTestData.parameterExpressions)
      )
      _ <- assertTestResultsAreNotTooBig(testResults)
    } yield ResultsWithCounts(testResults, computeCounts(canonical, testResults))
  }

  private def toCanonicalProcess(
      displayableProcess: DisplayableProcess
  )(implicit user: LoggedUser): CanonicalProcess = {
    val processResolver = processResolvers.forTypeUnsafe(displayableProcess.processingType)
    processResolver.validateAndResolve(displayableProcess)
  }

  private def assertTestResultsAreNotTooBig(testResults: TestResults): Future[Unit] = {
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

  private def computeCounts(canonical: CanonicalProcess, results: TestResults)(
      implicit loggedUser: LoggedUser
  ): Map[String, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(
        nresults.size.toLong,
        results.exceptions.find(_.nodeComponentInfo.map(_.nodeId).contains(key)).size.toLong
      )
    }
    processCounter.computeCounts(canonical, counts.get)
  }

}
