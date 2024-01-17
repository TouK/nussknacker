package pl.touk.nussknacker.ui.process.test

import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.{TestDataSettings, TestSourceParameters}
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

  def getTestingCapabilities(displayableProcess: DisplayableProcess, processName: ProcessName, isFragment: Boolean)(
      implicit user: LoggedUser
  ): TestingCapabilities = {
    val canonical = toCanonicalProcess(displayableProcess, processName, isFragment)
    testInfoProvider.getTestingCapabilities(canonical)
  }

  def testParametersDefinition(
      displayableProcess: DisplayableProcess,
      processName: ProcessName,
      isFragment: Boolean
  )(implicit user: LoggedUser): List[UISourceParameters] = {
    val canonical = toCanonicalProcess(displayableProcess, processName, isFragment)
    testInfoProvider
      .getTestParameters(canonical)
      .map { case (id, params) => UISourceParameters(id, params.map(DefinitionsService.createUIParameter)) }
      .toList
  }

  def generateData(
      displayableProcess: DisplayableProcess,
      processName: ProcessName,
      isFragment: Boolean,
      testSampleSize: Int
  )(
      implicit user: LoggedUser
  ): Either[String, RawScenarioTestData] = {
    val canonical = toCanonicalProcess(displayableProcess, processName, isFragment)

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
      isFragment: Boolean,
      rawTestData: RawScenarioTestData
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    for {
      preliminaryScenarioTestData <- preliminaryScenarioTestDataSerDe
        .deserialize(rawTestData)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      canonical = toCanonicalProcess(displayableProcess, idWithName.name, isFragment)
      scenarioTestData <- testInfoProvider
        .prepareTestData(preliminaryScenarioTestData, canonical)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      testResults <- testExecutorService.testProcess(
        idWithName,
        canonical,
        scenarioTestData
      )
      _ <- {
        assertTestResultsAreNotTooBig(testResults)
      }
    } yield ResultsWithCounts(testResults, computeCounts(canonical, testResults))
  }

  def performTest(
      idWithName: ProcessIdWithName,
      displayableProcess: DisplayableProcess,
      isFragment: Boolean,
      parameterTestData: TestSourceParameters
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts] = {
    val canonical = toCanonicalProcess(displayableProcess, idWithName.name, isFragment)
    for {
      testResults <- testExecutorService.testProcess(
        idWithName,
        canonical,
        ScenarioTestData(parameterTestData.sourceId, parameterTestData.parameterExpressions)
      )
      _ <- assertTestResultsAreNotTooBig(testResults)
    } yield ResultsWithCounts(testResults, computeCounts(canonical, testResults))
  }

  private def toCanonicalProcess(
      displayableProcess: DisplayableProcess,
      processName: ProcessName,
      isFragment: Boolean
  )(implicit user: LoggedUser): CanonicalProcess = {
    processResolver.validateAndResolve(displayableProcess, processName, isFragment)
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
