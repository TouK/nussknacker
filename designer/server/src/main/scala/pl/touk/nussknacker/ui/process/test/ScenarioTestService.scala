package pl.touk.nussknacker.ui.process.test

import cats.data.{EitherT, NonEmptyList, ValidatedNel}
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import cats.syntax.either._
import cats.syntax.list._
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import io.circe.{DecodingFailure, Json}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ScenarioCompilationErrors}
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.{ScenarioTestCommonFormatJsonRecord, ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.action.CommonModelDataInfoProvider
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder.TestRecordVariablesDecodingError
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ListUtil
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.ExpressionsToTestDataConversionError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService._
import pl.touk.nussknacker.ui.process.test.testcase._
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatHandler
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestService(
                           testDataSettings: TestDataSettings,
                           modelData: ModelData,
                           engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies],
                           processResolver: UIProcessResolver,
                           // These dependencies are needed for scenario testing execution
                           processCounter: ProcessCounter,
                           testExecutorService: ScenarioTestExecutorService,
                           uiProcessValidator: UIProcessValidator,
                           assertionVerifier: AssertionVerifier = new NoopAssertionVerifier,
                         ) extends LazyLogging {

  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  private val testDataFormatHandler = TestDataFormatHandler(testDataSettings.testDataFormat, modelData)

  private val preliminaryScenarioRecordsSerDe = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = testDataSettings.testDataMaxLength,
    maxRecordsCount = testDataSettings.maxSamplesCount,
    testDataFormatSerDe = testDataFormatHandler.serDe
  )

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  def getTestingCapabilities(
                              scenarioGraph: ScenarioGraph,
                              processVersion: ProcessVersion,
                            ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    val jobData = JobData(canonical.metaData, processVersion)
    val sources = canonical.collectAllSources

    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        sourceNel <- NonEmptyList
          .fromList(sources)
          .toRight[TestingCapabilitiesError](TestingCapabilitiesError.NoSourcesError)

        compiledSourcesNel <- sourceNel
          .map(compileSource)
          .sequence
          .leftMap(TestingCapabilitiesError.SourcesCompilationError)
          .toEither

        capabilitiesForEachSource = compiledSourcesNel.map { case (_, source) =>
          getTestingCapabilitiesForCompiledSource(source)
        }
      } yield {
        capabilitiesForEachSource.reduceLeft((tc1, tc2) =>
          TestingCapabilities(
            canBeTested = tc1.canBeTested || tc2.canBeTested,
            canFetchLiveData = tc1.canFetchLiveData || tc2.canFetchLiveData,
            canTestWithForm =
              tc1.canTestWithForm && tc2.canTestWithForm // TODO change to "or" after adding support for multiple sources
          )
        )
      }
    }
  }

  private def getTestingCapabilitiesForCompiledSource(compiledSource: Source): TestingCapabilities = {
    TestingCapabilities(
      canBeTested = testDataFormatHandler.canBeTested(compiledSource),
      canFetchLiveData = testDataFormatHandler.canFetchLiveData(compiledSource),
      canTestWithForm = testDataFormatHandler.canTestWithForm(compiledSource)
    )
  }

  def validateAndGetTestParametersDefinition(
                                              scenarioGraph: ScenarioGraph,
                                              processVersion: ProcessVersion,
                                              isFragment: Boolean
                                            )(implicit user: LoggedUser): Either[ParametersDefinitionError, Map[NodeId, List[Parameter]]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    val jobData = JobData(canonical.metaData, processVersion)
    val sources = canonical.collectAllSources
    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      val compiledSourcesById =
        sources.map(source => NodeId(source.id) -> commonModelDataInfoProvider.nodeCompiler.compileNode(source))
      compiledSourcesById
        .map { case (sourceId, sourceCompilationResult) =>
          testDataFormatHandler.getTestParametersDefinition(sourceId, sourceCompilationResult).map { parameters =>
            val enrichedParameters = StandardParameterEnrichment.enrichParameterDefinitions(
              original = parameters,
              parametersConfig = Map.empty,
              globalParametersConfig = modelData.modelConfig.globalParametersConfig
            )
            sourceId -> enrichedParameters
          }
        }
        .sequence
        .map(_.toMap)
    }

  }

  def fetchSourcesLiveData(
                            scenarioGraph: ScenarioGraph,
                            processVersion: ProcessVersion,
                            isFragment: Boolean,
                            maxNumberOfRecords: Int
                          )(
                            implicit user: LoggedUser
                          ): Either[FetchLiveDataError, SerializedScenarioRecordsContent] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    val jobData = JobData(canonical.metaData, processVersion)
    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        _ <- validateRecordsCount(maxNumberOfRecords)(FetchLiveDataError.TooManyRecordsRequestedError)

        compiledSources <- canonical.collectAllSources
          .map(compileSource)
          .sequence
          .leftMap[FetchLiveDataError](FetchLiveDataError.SourcesCompilationError)
          .toEither

        fetchedLiveDataForSources = compiledSources
          .flatMap { case (sourceId, compiledSource) =>
            testDataFormatHandler.fetchLiveData(sourceId, compiledSource, maxNumberOfRecords).toOption
          }

        fetchedLiveDataForSourcesNel <- fetchedLiveDataForSources.toNel.toRight(
          FetchLiveDataError.LiveDataFetchingNotSupportedError
        )

        mergedLivedData = ListUtil
          // We sort firstly in case if returner by the source records are not sorted
          // and then sort at the end after we merge elements from each list in round-robin manner
          .mergeListsRoundRobin(fetchedLiveDataForSourcesNel.toList.map(_.sorted), maxNumberOfRecords)
          .sorted

        fetchedLiveDataNel <- NonEmptyList
          .fromList(mergedLivedData)
          .toRight(
            FetchLiveDataError.NoLiveDataAvailableError
          )
        serializedLiveData <- preliminaryScenarioRecordsSerDe
          .serialize(PreliminaryScenarioRecords(fetchedLiveDataNel))
          .leftMap(FetchLiveDataError.ScenarioRecordsSerializationError)
      } yield serializedLiveData
    }
  }

  def fetchSourceLiveData(
                           metaData: MetaData,
                           sourceNodeData: SourceNodeData,
                           maxNumberOfRecords: Int
                         ): Either[FetchLiveDataError, SerializedScenarioRecordsContent] = {
    val jobData = JobData(metaData, ProcessVersion.empty)
    val nodeId = NodeId(sourceNodeData.id)

    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        _ <- validateRecordsCount(maxNumberOfRecords)(FetchLiveDataError.TooManyRecordsRequestedError)

        compiledSourceWithId <- compileSource(sourceNodeData)
          .leftMap(errors => FetchLiveDataError.SourcesCompilationError(errors))
          .toEither

        (_, compiledSource) = compiledSourceWithId

        fetchedLiveData <- testDataFormatHandler
          .fetchLiveData(nodeId, compiledSource, maxNumberOfRecords)
          .leftMap(_ => FetchLiveDataError.LiveDataFetchingNotSupportedError)
          .map(_.sorted)

        fetchedLiveDataNel <- NonEmptyList
          .fromList(fetchedLiveData)
          .toRight(
            FetchLiveDataError.NoLiveDataAvailableError
          )

        serializedLiveData <- preliminaryScenarioRecordsSerDe
          .serialize(PreliminaryScenarioRecords(fetchedLiveDataNel))
          .leftMap(serializationError => FetchLiveDataError.ScenarioRecordsSerializationError(serializationError))
      } yield serializedLiveData
    }
  }

  private def compileSource(
                             source: SourceNodeData
                           )(
                             implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
                           ): ValidatedNel[(NodeId, NonEmptyList[ProcessCompilationError]), (NodeId, Source)] = {
    val nodeId = NodeId(source.id)
    commonModelDataInfoProvider.nodeCompiler
      .compileNode(source)
      .compiledObject
      .leftMap { compilationErrors =>
        NonEmptyList.one(nodeId -> compilationErrors)
      }
      .map(nodeId -> _)
  }

  def performTest(
                   scenarioGraph: ScenarioGraph,
                   processVersion: ProcessVersion,
                   isFragment: Boolean,
                   parameterTestData: TestSourceParameters,
                 )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    val canonical = toCanonicalProcess(scenarioGraph, processVersion, isFragment)
    (for {
      testData <- EitherT.fromEither[Future][PerformTestError, ScenarioTestData](
        testDataFormatHandler
          .convertToTestData(
            parameterTestData.sourceId,
            parameterTestData.parameterExpressions
          )
          .leftMap(ExpressionsToTestDataConversionError)
      )

      testResults <- EitherT(
        performTestWithDeserializedRecords(processVersion, canonical, testData)
      )

      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
    } yield ResultsWithCounts(Instant.now(), testResults, computeCounts(canonical, isFragment, testResults))).value
  }

  def performTestCase(
                       scenarioGraph: ScenarioGraph,
                       processVersion: ProcessVersion,
                       isFragment: Boolean,
                       test: TestCase,
                     )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    (for {
      preliminaryScenarioTestRecords <- EitherT.fromEither[Future](
        preliminaryScenarioRecordsSerDe
          .deserialize(SerializedScenarioRecordsContent(test.inputs))
          .leftMap[PerformTestError](PerformTestError.DeserializationError)
      )

      canonical = toCanonicalProcess(
        scenarioGraph,
        processVersion,
        isFragment
      )

      scenarioTestData <- EitherT.fromEither[Future](prepareTestData(preliminaryScenarioTestRecords, canonical))

      // compile process/validate process to gather node typing needed for assertion expressions compilation
      nodeContextsTyping = compileScenarioAndExtractNodeContextsTyping(scenarioGraph, processVersion, isFragment)
      compiledTestCase = compileTestCase(test, nodeContextsTyping)

      testResults <- EitherT(
        performTestWithDeserializedRecords(processVersion, canonical, scenarioTestData)
      )

      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
      testResultsWithAssertionResults = verifyAssertions(compiledTestCase, testResults)
    } yield ResultsWithCounts(Instant.now(), testResultsWithAssertionResults, computeCounts(canonical, isFragment, testResults))).value
  }

  private def compileScenarioAndExtractNodeContextsTyping(scenarioGraph: ScenarioGraph,
                                                          processVersion: ProcessVersion,
                                                          isFragment: Boolean)(implicit user: LoggedUser): Map[String, NodeTypingData] = {
    val validationResult = uiProcessValidator.validate(scenarioGraph, processVersion, isFragment)
    if (validationResult.hasErrors) {
      //todo: to be decided if the operation can be called directly by rest api not by designer
      throw new IllegalStateException(s"Should not happen - only valid scenario should be allowed to be tested by designer. Scenario has errors: ${validationResult.errors}")
    }

    validationResult.nodeResults
  }

  private def compileTestCase(test: TestCase, nodesTyping: Map[String, NodeTypingData]): CompiledTestCase = {
    val testCompiler = new TestCaseCompiler(expressionCompiler)
    //todo: to be decided if the operation can be called directly by rest api not by designer
    testCompiler.compile(test, nodesTyping)
      .getOrElse(throw new IllegalStateException("Should not happen - only valid scenario should be allowed to be tested by designer"))
  }

  private def verifyAssertions(test: CompiledTestCase, testResults: TestResults[Json]): TestResults[Json] = {
    val assertionsResults = assertionVerifier.verify(test, testResults.originalNodeResults)
    testResults.copy(assertionsResults = assertionsResults)
  }

  def performTest(
                   scenarioGraph: ScenarioGraph,
                   processVersion: ProcessVersion,
                   isFragment: Boolean,
                   serializedTestRecordsContent: SerializedScenarioRecordsContent,
                 )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    (for {
      preliminaryScenarioTestRecords <- EitherT.fromEither[Future](
        preliminaryScenarioRecordsSerDe
          .deserialize(serializedTestRecordsContent)
          .leftMap[PerformTestError](PerformTestError.DeserializationError)
      )

      canonical = toCanonicalProcess(
        scenarioGraph,
        processVersion,
        isFragment
      )

      scenarioTestData <- EitherT.fromEither[Future](prepareTestData(preliminaryScenarioTestRecords, canonical))

      testResults <- EitherT(
        performTestWithDeserializedRecords(processVersion, canonical, scenarioTestData)
      )

      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
    } yield ResultsWithCounts(Instant.now(), testResults, computeCounts(canonical, isFragment, testResults))).value
  }

  private def performTestWithDeserializedRecords(
                                                  processVersion: ProcessVersion,
                                                  canonical: CanonicalProcess,
                                                  scenarioTestData: ScenarioTestData
                                                )(implicit ec: ExecutionContext, user: LoggedUser) = {
    testExecutorService
      .testProcess(
        processVersion,
        canonical,
        scenarioTestData,
      )
      .map(Right[PerformTestError, TestResults[Json]])
      .recoverWith[Either[PerformTestError, TestResults[Json]]] {
        // Lite engine
        case decodingError: TestRecordVariablesDecodingError =>
          Future.successful(Left(toPerformTestError(decodingError)))
        // Flink engine
        case scenarioCompilationErrors: ScenarioCompilationErrors =>
          scenarioCompilationErrors.errors
            // TODO: Redesign StubbedFlinkProcessCompilerDataFactory to remove error nesting
            .collectFirst { case CannotCreateObjectError(_, _, Some(decodingError: TestRecordVariablesDecodingError)) =>
              Future.successful(Left(toPerformTestError(decodingError)))
            }
            .getOrElse {
              throw scenarioCompilationErrors
            }
      }
  }

  private def toPerformTestError(decodingError: TestRecordVariablesDecodingError): PerformTestError = {
    decodingError match {
      case CommonTestDataFormatVariablesDecoder
      .UnexpectedVariableInTestRecordError(variableName, sourceId, testRecordIndex) =>
        PerformTestError.UnexpectedVariableInTestRecordError(variableName, sourceId, testRecordIndex)
      case CommonTestDataFormatVariablesDecoder
      .TestRecordVariableDecodingError(
      variableName,
      variableType,
      encodedVariable,
      cause,
      sourceId,
      testRecordIndex,
      ) =>
        PerformTestError
          .TestRecordVariableDecodingError(
            variableName,
            variableType,
            encodedVariable,
            cause,
            sourceId,
            testRecordIndex
          )
    }
  }

  private[test] def prepareTestData(
                                     preliminaryScenarioRecords: PreliminaryScenarioRecords,
                                     scenario: CanonicalProcess
                                   ): Either[PerformTestError, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = scenario.collectAllSources.map(n => NodeId(n.id)).toSet
    preliminaryScenarioRecords.records.zipWithIndex
      .map {
        case (SourceSpecificFormatPreliminaryScenarioRecord(sourceId, record, timestamp), _)
          if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestSourceSpecificFormatJsonRecord(sourceId, record, timestamp))
        case (CommonFormatPreliminaryScenarioRecord(sourceId, variables, timestamp), _)
          if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestCommonFormatJsonRecord(sourceId, variables, timestamp))
        case (record, recordIdx) =>
          Left(PerformTestError.MissingSourceError(record.sourceId, recordIdx))
      }
      .sequence
      .map(scenarioTestRecords => ScenarioTestData(scenarioTestRecords.toList))
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

  def validateRecordsCount[E](size: Int)(tooManyRecordsError: Int => E): Either[E, Unit] = {
    testDataSettings.maxSamplesCount
      .map { definedMaxSampleSize =>
        Either.cond(
          size <= definedMaxSampleSize,
          (),
          tooManyRecordsError(definedMaxSampleSize)
        )
      }
      .getOrElse(Right(()))
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
    testDataSettings.resultsMaxBytes
      .map { definedResultsMaxBytes =>
        val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
        Either.cond(
          testDataResultApproxByteSize <= definedResultsMaxBytes,
          (), {
            logger.whenDebugEnabled {
              val fieldSizes = testResults.getClass.getDeclaredFields
                .map { field =>
                  field.setAccessible(true)
                  val fieldSize = RamUsageEstimator.sizeOf(field.get(testResults))
                  s"${field.getName}=$fieldSize"
                }
                .mkString(", ")
              logger.debug(
                s"Test results are too big: $testDataResultApproxByteSize > $definedResultsMaxBytes; $fieldSizes"
              )
            }
            PerformTestError.TestResultsSizeExceededError(testDataResultApproxByteSize, definedResultsMaxBytes)
          }
        )
      }
      .getOrElse(Right(()))
  }

  private def computeCounts(canonical: CanonicalProcess, isFragment: Boolean, results: TestResults[_])(
    implicit loggedUser: LoggedUser
  ): Map[NodeId, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(
        nresults.size.toLong,
        results.exceptions.find(_.nodeId.contains(key)).size.toLong
      )
    }
    processCounter.computeCounts(canonical, isFragment, counts.get)
  }

  private def withScenarioCompilationDependencies[T](jobData: JobData)(
    f: ScenarioCompilationDependencies => T
  ) =
    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          f(new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies))
        }
      }
      .unsafeRunSync()

}

object ScenarioTestService {

  sealed trait TestingCapabilitiesError

  object TestingCapabilitiesError {
    case object NoSourcesError extends TestingCapabilitiesError

    case class SourcesCompilationError(
                                        nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
                                      ) extends TestingCapabilitiesError

  }

  sealed trait ParametersDefinitionError

  object ParametersDefinitionError {

    final case class SourcesCompilationError(
                                              nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
                                            ) extends ParametersDefinitionError

    final case class TestingWithCustomInputNotSupportedError(nodeId: NodeId) extends ParametersDefinitionError

  }

  sealed trait FetchLiveDataError

  object FetchLiveDataError {

    final case class SourcesCompilationError(
                                              nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
                                            ) extends FetchLiveDataError

    final case object NoLiveDataAvailableError extends FetchLiveDataError

    final case object LiveDataFetchingNotSupportedError extends FetchLiveDataError

    final case class ScenarioRecordsSerializationError(cause: PreliminaryScenarioRecordsSerDe.SerializationError)
      extends FetchLiveDataError

    final case class TooManyRecordsRequestedError(maxRecordsCount: Int) extends FetchLiveDataError
  }

  sealed trait PerformTestError

  object PerformTestError {
    final case class DeserializationError(cause: PreliminaryScenarioRecordsSerDe.DeserializationError)
      extends PerformTestError

    final case class ExpressionsToTestDataConversionError(
                                                           cause: TestDataFormatHandler.ExpressionsToTestDataConversionError
                                                         ) extends PerformTestError

    final case class UnexpectedVariableInTestRecordError(variableName: String, sourceId: NodeId, testRecordIndex: Int)
      extends PerformTestError

    final case class TestRecordVariableDecodingError(
                                                      variableName: String,
                                                      variableType: TypingResult,
                                                      encodedVariable: Json,
                                                      cause: DecodingFailure,
                                                      sourceId: NodeId,
                                                      testRecordIndex: Int,
                                                    ) extends PerformTestError

    final case class MissingSourceError(sourceId: NodeId, recordIndex: Int) extends PerformTestError

    final case class TestResultsSizeExceededError(approxSizeInBytes: Long, maxBytes: Long) extends PerformTestError
  }

}
