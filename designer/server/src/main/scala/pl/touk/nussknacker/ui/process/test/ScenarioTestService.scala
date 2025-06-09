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

  private val preliminaryScenarioTestDataSerDe = new PreliminaryScenarioTestDataSerDe(
    testDataMaxLength = testDataSettings.testDataMaxLength,
    maxSamplesCount = testDataSettings.maxSamplesCount
  )

  def getTestingCapabilities(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
  ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
    val jobData   = JobData(canonical.metaData, processVersion)
    val sources   = commonModelDataInfoProvider.collectAllSources(canonical)

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
    val sources   = commonModelDataInfoProvider.collectAllSources(canonical)
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
      .map(StandardParameterEnrichment.enrichParameterDefinitions(_, Map.empty))
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
      case _ => Left(ParametersDefinitionError.UnsupportedTestingWithCustomInputError(sourceId))
    }
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
    val jobData   = JobData(canonical.metaData, processVersion)
    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        _ <- validateSampleSize(maxNumberOfSamples)(FetchLiveDataError.TooManySamplesRequestedError)

        compiledSources <- commonModelDataInfoProvider
          .collectAllSources(canonical)
          .map(compileSource)
          .sequence
          .leftMap[FetchLiveDataError](FetchLiveDataError.SourcesCompilationError)
          .toEither

        generatorsByNodeId = compiledSources.flatMap { case (nodeId, compiledSource) =>
          compiledSource.cast[TestDataGenerator].map(nodeId -> _)
        }

        generatorsByNodeIdNel <- generatorsByNodeId.toNel.toRight(
          FetchLiveDataError.NoSourcesWithLiveDataFetchingSupport
        )

        testData <- createPreliminaryTestData(generatorsByNodeIdNel, maxNumberOfSamples).toRight(
          FetchLiveDataError.NoLiveDataAvailable
        )
        rawTestData <- preliminaryScenarioTestDataSerDe
          .serialize(testData)
          .leftMap(FetchLiveDataError.ScenarioTestDataSerializationError)
      } yield rawTestData
    }
  }

  def fetchSourceLiveData(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      maxNumberOfSamples: Int
  ): Either[SourceTestError, RawScenarioTestData] = {
    val jobData = JobData(metaData, ProcessVersion.empty)
    val nodeId  = NodeId(sourceNodeData.id)

    withScenarioCompilationDependencies(jobData) { implicit scenarioCompilationDependencies =>
      for {
        _ <- validateSampleSize(maxNumberOfSamples)(SourceTestError.TooManySamplesRequestedError)

        compiledSourceWithId <- compileSource(sourceNodeData)
          .leftMap(errors => SourceTestError.SourceCompilationError(nodeId.id, errors.toList.map(_.toString)))
          .toEither

        (_, compiledSource) = compiledSourceWithId

        // We assume that TestDataGenerator.generateTestData implementation will always fetch live data
        // TODO: In the future we want to extract another interface which would explicitly fetch live data in the standardized format which would not require to define TestRecordParser
        testDataGenerator <- compiledSource
          .cast[TestDataGenerator]
          .toRight(SourceTestError.UnsupportedSourcePreviewError(nodeId.id))

        testData <- createPreliminaryTestData(NonEmptyList.one(nodeId -> testDataGenerator), maxNumberOfSamples)
          .toRight(SourceTestError.NoLiveDataFetchedError)

        rawTestData <- preliminaryScenarioTestDataSerDe
          .serialize(testData)
          .leftMap(serializationError => SourceTestError.ScenarioTestDataSerializationError(serializationError))
      } yield rawTestData
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

  private def createPreliminaryTestData(
      generators: NonEmptyList[(NodeId, TestDataGenerator)],
      size: Int
  ): Option[PreliminaryScenarioTestData] = {
    val fetchedLiveData        = fetchLiveData(generators, size)
    val sortedRecords          = fetchedLiveData.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    val preliminaryTestRecords = sortedRecords.map(PreliminaryScenarioTestRecord.apply)
    NonEmptyList
      .fromList(preliminaryTestRecords)
      .map(PreliminaryScenarioTestData.apply)
  }

  private def fetchLiveData(generators: NonEmptyList[(NodeId, TestDataGenerator)], size: Int) = {
    // method TestDataGenerator.generateTestData has to be called within ModelClassLoader context
    modelData.withModelClassloaderAsContextClassLoader {
      val sourceTestDataList = generators.map { case (sourceId, testDataGenerator) =>
        val sourceTestRecords = testDataGenerator.generateTestData(size).testRecords
        sourceTestRecords.map(testRecord => ScenarioTestJsonRecord(sourceId, testRecord))
      }
      ListUtil.mergeLists(sourceTestDataList.toList, size)
    }
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

      scenarioTestData <- EitherT.fromEither[Future](prepareTestData(preliminaryScenarioTestData, canonical))

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
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[PerformTestError, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = commonModelDataInfoProvider.collectAllSources(scenario).map(_.id).toSet
    preliminaryTestData.testRecords.zipWithIndex
      .map {
        case (PreliminaryScenarioTestRecord(sourceId, record, timestamp), _)
            if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestJsonRecord(sourceId, record, timestamp))
        case (PreliminaryScenarioTestRecord(sourceId, _, _), recordIdx) =>
          Left(PerformTestError.MissingSource(NodeId(sourceId), recordIdx))
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

  def validateSampleSize[E](size: Int)(tooManySamplesError: Int => E): Either[E, Unit] = {
    testDataSettings.maxSamplesCount
      .map { definedMaxSampleSize =>
        Either.cond(
          size <= definedMaxSampleSize,
          (),
          tooManySamplesError(definedMaxSampleSize)
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
          (),
          PerformTestError.TestResultsSizeExceeded(testDataResultApproxByteSize, definedResultsMaxBytes)
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

    final case class UnsupportedTestingWithCustomInputError(nodeId: NodeId) extends ParametersDefinitionError

  }

  sealed trait FetchLiveDataError

  object FetchLiveDataError {

    final case class SourcesCompilationError(
        nodesWithErrors: NonEmptyList[(NodeId, NonEmptyList[ProcessCompilationError])]
    ) extends FetchLiveDataError

    final case object NoLiveDataAvailable                  extends FetchLiveDataError
    final case object NoSourcesWithLiveDataFetchingSupport extends FetchLiveDataError
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
    final case class MissingSource(sourceId: NodeId, recordIndex: Int)                extends PerformTestError
    final case class TestResultsSizeExceeded(approxSizeInBytes: Long, maxBytes: Long) extends PerformTestError
  }

}
