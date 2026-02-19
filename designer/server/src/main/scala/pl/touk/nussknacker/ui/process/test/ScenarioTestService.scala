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
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ScenarioCompilationErrors}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.{
  ScenarioTestCommonFormatJsonRecord,
  ScenarioTestData,
  ScenarioTestSourceSpecificFormatJsonRecord
}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.action.CommonModelDataInfoProvider
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.test.testcase.{EnricherMock, TestCase}
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder.TestRecordVariablesDecodingError
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ListUtil
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationErrors}
import pl.touk.nussknacker.ui.api.{TestDataFormat, TestDataSettings}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.{
  AssertionErrors,
  ExpressionsToTestDataConversionError,
  MockConfiguredForNotExistingNodesError,
  ScenarioValidationError
}
import pl.touk.nussknacker.ui.process.test.testcase._
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatHandler
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

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
) extends LazyLogging {

  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  private val testDataFormatHandler = TestDataFormatHandler(testDataSettings.testDataFormat, modelData)

  private val preliminaryScenarioRecordsSerDe = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = testDataSettings.testDataMaxLength,
    maxRecordsCount = testDataSettings.maxSamplesCount,
    testDataFormatSerDe = testDataFormatHandler.serDe
  )

  // for test cases only common data format is used - can be removed if we drop source specific format support
  private val testCasePreliminaryScenarioRecordsSerDe = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = testDataSettings.testDataMaxLength,
    maxRecordsCount = testDataSettings.maxSamplesCount,
    testDataFormatSerDe = TestDataFormatHandler(TestDataFormat.CommonFormat, modelData).serDe
  )

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)

  private val assertionCompiler                    = new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
  private val assertionVerifier: AssertionVerifier = new AssertionVerifier(globalVariablesPreparer)

  def getTestingCapabilities(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
  ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    val jobData   = JobData(canonical.metaData, processVersion)
    val sources   = canonical.collectAllSources

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
    val jobData   = JobData(canonical.metaData, processVersion)
    val sources   = canonical.collectAllSources
    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      val compiledSourcesById =
        sources.map(source => source.id -> commonModelDataInfoProvider.nodeCompiler.compileNode(source))
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
    val jobData   = JobData(canonical.metaData, processVersion)
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
    val nodeId  = sourceNodeData.id

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
    val nodeId = source.id
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
      testCase: TestCase,
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[PerformTestError, ResultsWithCounts]] = {
    val jobData = JobData(scenarioGraph.toMetaData(processVersion.processName), processVersion)
    (for {
      preliminaryScenarioTestRecords <- EitherT.fromEither[Future](
        testCasePreliminaryScenarioRecordsSerDe
          .deserialize(SerializedScenarioRecordsContent(testCase.inputs))
          .leftMap[PerformTestError](PerformTestError.DeserializationError)
      )
      graphWithSubstitutedMocks <- EitherT.fromEither[Future](validateAndSubstituteMocks(scenarioGraph, testCase.mocks))
      scenarioWithTyping <- EitherT.fromEither[Future](
        validateScenario(graphWithSubstitutedMocks, processVersion, isFragment)
      )

      scenarioTestData <- EitherT.fromEither[Future](
        prepareTestData(preliminaryScenarioTestRecords, scenarioWithTyping.scenario)
      )
      compiledAssertions <- EitherT.fromEither[Future](
        compileAssertions(testCase, scenarioWithTyping.nodesTyping, jobData)
      )
      testResults <- EitherT(
        performTestWithDeserializedRecords(processVersion, scenarioWithTyping.scenario, scenarioTestData)
      )

      _ <- EitherT.fromEither[Future](validateTestResultsAreNotTooBig(testResults))
    } yield ResultsWithCounts(
      Instant.now(),
      testResults,
      computeCounts(scenarioWithTyping.scenario, isFragment, testResults),
      assertionVerifier.verify(compiledAssertions, testResults.originalNodeResults, jobData)
    )).value
  }

  private def validateAndSubstituteMocks(
      scenarioGraph: ScenarioGraph,
      mocks: Map[NodeId, EnricherMock]
  ): Either[PerformTestError, ScenarioGraph] = {
    val existingNodesId           = scenarioGraph.nodes.map(node => node.id).toSet
    val mockedNodesId             = mocks.keySet
    val notExistingNodesWithMocks = mockedNodesId.diff(existingNodesId)

    if (notExistingNodesWithMocks.nonEmpty) {
      Left(MockConfiguredForNotExistingNodesError(NonEmptyList.fromListUnsafe(notExistingNodesWithMocks.toList)))
    } else {
      Right(substituteMocks(scenarioGraph, mocks))
    }
  }

  private def substituteMocks(scenarioGraph: ScenarioGraph, mocks: Map[NodeId, EnricherMock]) = {
    val nodesWithSubstitutions = scenarioGraph.nodes.map {
      case nodeData @ (enricher: node.Enricher) =>
        val enricherMockOpt = mocks.get(nodeData.id).map(_.expression)
        // we use provided mock or the one specified in original scenario
        enricher.copy(mockExpression = enricherMockOpt.orElse(enricher.mockExpression))
      case data => data
    }
    scenarioGraph.copy(nodes = nodesWithSubstitutions)
  }

  private def validateScenario(scenarioGraph: ScenarioGraph, processVersion: ProcessVersion, isFragment: Boolean)(
      implicit user: LoggedUser
  ): Either[ScenarioValidationError, ScenarioWithTyping] = {
    val validationResult = processResolver.validateBeforeUiResolving(scenarioGraph, processVersion, isFragment)
    val canonicalProcess =
      processResolver.resolveExpressions(scenarioGraph, processVersion.processName, validationResult.typingInfo)
    if (validationResult.hasErrors) {
      Left(ScenarioValidationError(validationResult.errors))
    } else {
      Right(ScenarioWithTyping(canonicalProcess, validationResult.nodeResults))
    }
  }

  private def compileAssertions(
      test: TestCase,
      nodesTyping: Map[String, NodeTypingData],
      jobData: JobData
  ): Either[PerformTestError, CompiledAssertions] = {
    assertionCompiler
      .compile(test, nodesTyping, jobData)
      .fold(
        errors => Left(AssertionErrors(errors)),
        Right(_)
      )
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

    val allScenarioSourceIds = scenario.collectAllSources.map(n => n.id).toSet
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

  private final case class ScenarioWithTyping(scenario: CanonicalProcess, nodesTyping: Map[String, NodeTypingData])

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

    final case class AssertionErrors(errors: NonEmptyList[testcase.AssertionError]) extends PerformTestError

    final case class MockConfiguredForNotExistingNodesError(notExistingNodeIds: NonEmptyList[NodeId])
        extends PerformTestError

    final case class ScenarioValidationError(errors: ValidationErrors) extends PerformTestError

  }

}
