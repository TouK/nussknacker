package pl.touk.nussknacker.ui.process.test

import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving

import scala.concurrent.{ExecutionContext, Future}

object ScenarioTestService {

  def apply(providers: ProcessingTypeDataProvider[ModelData],
            testDataSettings: TestDataSettings,
            processResolving: UIProcessResolving,
            processCounter: ProcessCounter,
            testExecutorService: ScenarioTestExecutorService,
           ): ScenarioTestService = {
    new ScenarioTestService(
      providers.mapValues(new ModelDataTestInfoProvider(_)),
      testDataSettings,
      new ScenarioTestDataSerDe(testDataSettings),
      processResolving,
      processCounter,
      testExecutorService,
    )
  }

}

class ScenarioTestService(testInfoProviders: ProcessingTypeDataProvider[TestInfoProvider],
                          testDataSettings: TestDataSettings,
                          scenarioTestDataSerDe: ScenarioTestDataSerDe,
                          processResolving: UIProcessResolving,
                          processCounter: ProcessCounter,
                          testExecutorService: ScenarioTestExecutorService,
                         ) extends LazyLogging {

  def getTestingCapabilities(displayableProcess: DisplayableProcess): TestingCapabilities = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    val canonical = toCanonicalProcess(displayableProcess)
    testInfoProvider.getTestingCapabilities(canonical)
  }

  def generateData(displayableProcess: DisplayableProcess, testSampleSize: Int): Either[String, RawScenarioTestData] = {
    val testInfoProvider = testInfoProviders.forTypeUnsafe(displayableProcess.processingType)
    val canonical = toCanonicalProcess(displayableProcess)

    for {
      _ <- Either.cond(testSampleSize <= testDataSettings.maxSamplesCount, (), s"Too many samples requested, limit is ${testDataSettings.maxSamplesCount}")
      generatedData <- testInfoProvider.generateTestData(canonical, testSampleSize).toRight("Test data could not be generated for scenario")
      rawTestData <- scenarioTestDataSerDe.serializeTestData(generatedData)
    } yield rawTestData
  }

  def performTest[T](idWithName: ProcessIdWithName,
                     displayableProcess: DisplayableProcess,
                     rawTestData: RawScenarioTestData,
                     testResultsVariableEncoder: Any => T)
                    (implicit ec: ExecutionContext, user: LoggedUser): Future[ResultsWithCounts[T]] = {
    for {
      scenarioTestData <- scenarioTestDataSerDe.prepareTestData(rawTestData)
        .fold(error => Future.failed(new IllegalArgumentException(error)), Future.successful)
      canonical = toCanonicalProcess(displayableProcess)
      testResults <- testExecutorService.testProcess(idWithName, canonical, displayableProcess.category, scenarioTestData, testResultsVariableEncoder)
      _ <- assertTestResultsAreNotTooBig(testResults)
    } yield ResultsWithCounts(testResults, computeCounts(canonical, testResults))
  }

  private def toCanonicalProcess(displayableProcess: DisplayableProcess): CanonicalProcess = {
    val validationResult = processResolving.validateBeforeUiResolving(displayableProcess)
    processResolving.resolveExpressions(displayableProcess, validationResult.typingInfo)
  }

  private def assertTestResultsAreNotTooBig(testResults: TestResults[_]): Future[Unit] = {
    val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
    if (testDataResultApproxByteSize > testDataSettings.resultsMaxBytes) {
      logger.info(s"Test data limit exceeded. Approximate test data size: $testDataResultApproxByteSize, but limit is: ${testDataSettings.resultsMaxBytes}")
      Future.failed(new RuntimeException("Too much test data. Please decrease test input data size."))
    } else {
      Future.successful(())
    }
  }

  private def computeCounts(canonical: CanonicalProcess, results: TestResults[_]): Map[String, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(nresults.size.toLong, results.exceptions.find(_.nodeId.contains(key)).size.toLong)
    }
    processCounter.computeCounts(canonical, counts.get)
  }
}
