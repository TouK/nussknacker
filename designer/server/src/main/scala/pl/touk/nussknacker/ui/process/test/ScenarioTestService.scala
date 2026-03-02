package pl.touk.nussknacker.ui.process.test

import cats.data.{EitherT, NonEmptyList, ValidatedNel}
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import cats.syntax.either._
import cats.syntax.list._
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestDataGenerator, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.definition.action.CommonModelDataInfoProvider
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.ListUtil
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService._
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import shapeless.syntax.typeable.typeableOps

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

  private val preliminaryScenarioRecordsSerDe = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = testDataSettings.testDataMaxLength,
    maxRecordsCount = testDataSettings.maxSamplesCount
  )

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
      canBeTested = compiledSource.isInstanceOf[SourceTestSupport[_]],
      canFetchLiveData = compiledSource.isInstanceOf[TestDataGenerator],
      canTestWithForm = compiledSource.isInstanceOf[TestWithParametersSupport[_]]
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
      for {
        compiledSourcesById <-
          sources.map(compileSource).sequence.leftMap(ParametersDefinitionError.SourcesCompilationError).toEither

        parametersBySourceId <- compiledSourcesById.map { case (sourceId, compiledSource) =>
          getTestParametersWithDefaults(sourceId, compiledSource).map(sourceId -> _)
        }.sequence
      } yield parametersBySourceId.toMap
    }
  }

  private def getTestParametersWithDefaults(
      sourceId: NodeId,
      compiledSource: Source,
  ): Either[ParametersDefinitionError, List[Parameter]] = {
    getTestParameters(sourceId, compiledSource)
      .map(
        StandardParameterEnrichment.enrichParameterDefinitions(
          _,
          Map.empty,
          modelData.modelConfig.globalParametersConfig
        )
      )
  }

  // Currently we rely on the assumption that client always call scenarioTesting / {scenarioName} / parameters endpoint
  // only when scenarioTesting / {scenarioName} / capabilities endpoint returns canTestWithForm = true. Because of that
  // for non happy-path cases we throw NotSupportedBySource and causes error notification on FE
  // TODO: This assumption is wrong. Every endpoint should be treated separately.
  private def getTestParameters(
      sourceId: NodeId,
      compiledSource: Source,
  ): Either[ParametersDefinitionError, List[Parameter]] = {
    compiledSource match {
      case s: TestWithParametersSupport[_] => Right(s.testParametersDefinition)
      case _ => Left(ParametersDefinitionError.TestingWithCustomInputNotSupportedError(sourceId))
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
            fetchLiveData(sourceId, compiledSource, maxNumberOfRecords)
          }

        fetchedLiveDataForSourcesNel <- fetchedLiveDataForSources.toNel.toRight(
          FetchLiveDataError.LiveDataFetchingNotSupportedError
        )

        mergedLivedData = ListUtil
          .mergeLists(fetchedLiveDataForSourcesNel.toList, maxNumberOfRecords)
          .sortBy(_.timestamp.getOrElse(Long.MaxValue))

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
    val nodeId  = NodeId(sourceNodeData.id)

    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        _ <- validateRecordsCount(maxNumberOfRecords)(FetchLiveDataError.TooManyRecordsRequestedError)

        compiledSourceWithId <- compileSource(sourceNodeData)
          .leftMap(errors => FetchLiveDataError.SourcesCompilationError(errors))
          .toEither

        (_, compiledSource) = compiledSourceWithId

        fetchedLiveData <- fetchLiveData(nodeId, compiledSource, maxNumberOfRecords)
          .toRight(FetchLiveDataError.LiveDataFetchingNotSupportedError)

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
    commonModelDataInfoProvider
      .compileSourceNode(source)
      .leftMap { compilationErrors =>
        NonEmptyList.one(nodeId -> compilationErrors)
      }
      .map(nodeId -> _)
  }

  private def fetchLiveData(
      sourceId: NodeId,
      compiledSource: Source,
      maxNumberOfRecords: Int
  ): Option[List[PreliminaryScenarioRecord]] = {
    compiledSource.cast[TestDataGenerator].map { testDataGenerator =>
      val sourceTestRecords = modelData.withModelClassloaderAsContextClassLoader {
        testDataGenerator.generateTestData(maxNumberOfRecords).testRecords
      }
      sourceTestRecords
        .map(testRecord => PreliminaryScenarioRecord(sourceId.id, testRecord.json, testRecord.timestamp))
    }
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

  private[test] def prepareTestData(
      preliminaryScenarioRecords: PreliminaryScenarioRecords,
      scenario: CanonicalProcess
  ): Either[PerformTestError, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = scenario.collectAllSources.map(_.id).toSet
    preliminaryScenarioRecords.records.zipWithIndex
      .map {
        case (PreliminaryScenarioRecord(sourceId, record, timestamp), _) if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestJsonRecord(sourceId, record, timestamp))
        case (PreliminaryScenarioRecord(sourceId, _, _), recordIdx) =>
          Left(PerformTestError.MissingSourceError(NodeId(sourceId), recordIdx))
      }
      .sequence
      .map(scenarioTestRecords => ScenarioTestData(scenarioTestRecords.toList))
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
  ): Map[String, NodeCount] = {
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

    final case object NoLiveDataAvailableError          extends FetchLiveDataError
    final case object LiveDataFetchingNotSupportedError extends FetchLiveDataError
    final case class ScenarioRecordsSerializationError(cause: PreliminaryScenarioRecordsSerDe.SerializationError)
        extends FetchLiveDataError
    final case class TooManyRecordsRequestedError(maxRecordsCount: Int) extends FetchLiveDataError
  }

  sealed trait PerformTestError

  object PerformTestError {
    final case class DeserializationError(cause: PreliminaryScenarioRecordsSerDe.DeserializationError)
        extends PerformTestError
    final case class MissingSourceError(sourceId: NodeId, recordIndex: Int)                extends PerformTestError
    final case class TestResultsSizeExceededError(approxSizeInBytes: Long, maxBytes: Long) extends PerformTestError
    final case class ScenarioNodeValidationErrors(errors: List[NodeValidationError])       extends PerformTestError
  }

}
