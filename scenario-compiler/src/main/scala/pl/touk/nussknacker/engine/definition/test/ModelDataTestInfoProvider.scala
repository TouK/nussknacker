package pl.touk.nussknacker.engine.definition.test

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.action.CommonModelDataInfoProvider
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider._
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.util.ListUtil
import shapeless.syntax.typeable._

class ModelDataTestInfoProvider(
    modelData: ModelData,
    engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies]
) extends TestInfoProvider
    with LazyLogging {
  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  override def getTestingCapabilities(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    val jobData = JobData(scenario.metaData, processVersion)
    val sources = commonModelDataInfoProvider.collectAllSources(scenario)

    withScenarioCompilationDependencies(jobData) { scenarioCompilationDependencies =>
      if (sources.isEmpty) {
        Left(TestingCapabilitiesError.NoSourcesError)
      } else {
        val capabilities = sources.map(getTestingCapabilities(_, scenarioCompilationDependencies))
        val (_, rights) = capabilities.foldLeft(
          (List.empty[TestingCapabilitiesError], List.empty[TestingCapabilities])
        ) {
          case ((ls, rs), Left(l))  => (l :: ls, rs)
          case ((ls, rs), Right(r)) => (ls, r :: rs)
        }
        val successes = rights.reverse
        successes match {
          case Nil =>
            if (sources.isEmpty) Left(TestingCapabilitiesError.NoSourcesError)
            else Left(TestingCapabilitiesError.SourceCompilationError)
          case list =>
            Right(
              list.reduce((tc1, tc2) =>
                TestingCapabilities(
                  canBeTested = tc1.canBeTested || tc2.canBeTested,
                  canGenerateTestData = tc1.canGenerateTestData || tc2.canGenerateTestData,
                  canTestWithForm =
                    tc1.canTestWithForm && tc2.canTestWithForm // TODO change to "or" after adding support for multiple sources
                )
              )
            )
        }
      }
    }
  }

  private def getTestingCapabilities(
      source: SourceNodeData,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[TestingCapabilitiesError, TestingCapabilities] = {
    (for {
      sourceObj <- commonModelDataInfoProvider.compileSourceNode(source)(
        scenarioCompilationDependencies,
        NodeId(source.id)
      )
      canTest         = sourceObj.isInstanceOf[SourceTestSupport[_]]
      canGenerateData = sourceObj.isInstanceOf[TestDataGenerator]
      canTestWithForm = sourceObj.isInstanceOf[TestWithParametersSupport[_]]
    } yield TestingCapabilities(
      canBeTested = canTest,
      canGenerateTestData = canGenerateData,
      canTestWithForm = canTestWithForm
    )).toEither.left.map(_ => TestingCapabilitiesError.SourceCompilationError)
  }

  override def getTestParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[ParametersDefinitionError, Map[String, List[Parameter]]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    val sources = commonModelDataInfoProvider.collectAllSources(scenario)
    withScenarioCompilationDependencies(jobData) { scenarioCompilationDependencies =>
      val result = sources.foldLeft[Either[ParametersDefinitionError, List[(String, List[Parameter])]]](Right(Nil)) {
        case (accEither, source) =>
          for {
            acc    <- accEither
            params <- getTestParametersWithDefaults(source, scenarioCompilationDependencies)
          } yield (source.id -> params) :: acc
      }
      result.map(_.toMap)
    }
  }

  private def getTestParametersWithDefaults(
      source: SourceNodeData,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[ParametersDefinitionError, List[Parameter]] = {
    getTestParameters(source, scenarioCompilationDependencies)
      .map(StandardParameterEnrichment.enrichParameterDefinitions(_, Map.empty))
  }

  // Currently we rely on the assumption that client always call scenarioTesting / {scenarioName} / parameters endpoint
  // only when scenarioTesting / {scenarioName} / capabilities endpoint returns canTestWithForm = true. Because of that
  // for non happy-path cases we throw UnsupportedOperationException
  // TODO: This assumption is wrong. Every endpoint should be treated separately. Currently from time to time
  //       users got error notification because this endpoint is called without checking canTestWithForm = true.
  //       We can go even further and merge both endpoints
  private def getTestParameters(
      source: SourceNodeData,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[ParametersDefinitionError, List[Parameter]] = {
    commonModelDataInfoProvider.compileSourceNode(source)(scenarioCompilationDependencies, NodeId(source.id)) match {
      case Valid(s: TestWithParametersSupport[_]) => Right(s.testParametersDefinition)
      case Valid(sourceWithoutTestWithParametersSupport) =>
        Left(
          ParametersDefinitionError.NotSupportedBySource(
            s"Requested test parameters from source [${source.id}] of [${sourceWithoutTestWithParametersSupport.getClass.getName}] class that does not implement TestWithParametersSupport."
          )
        )
      case Invalid(errors) =>
        Left(
          ParametersDefinitionError.SourceValidationError(
            s"Requested test parameters from source [${source.id}] that is not valid. Errors: ${errors.toList.mkString(", ")}"
          )
        )
    }
  }

  override def generateTestData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      size: Int
  ): Either[ScenarioTestDataGenerationError, PreliminaryScenarioTestData] = {
    val jobData = JobData(scenario.metaData, processVersion)
    withScenarioCompilationDependencies(jobData) { scenarioCompilationDependencies =>
      for {
        generators <- prepareTestDataGenerators(scenario, scenarioCompilationDependencies)
        result <- createPreliminaryTestData(generators, size)
          .toRight(ScenarioTestDataGenerationError.NoDataGenerated)
      } yield result
    }
  }

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[SourceTestDataGenerationError, PreliminaryScenarioTestData] = {
    val jobData = JobData(metaData, ProcessVersion.empty)
    withScenarioCompilationDependencies(jobData) { scenarioCompilationDependencies =>
      val nodeId = NodeId(sourceNodeData.id)
      for {
        compiledSource <- commonModelDataInfoProvider
          .compileSourceNode(sourceNodeData)(scenarioCompilationDependencies, nodeId)
          .toEither
          .left
          .map(errors => SourceTestDataGenerationError.SourceCompilationError(nodeId, errors))
        testDataGenerator <- compiledSource
          .cast[TestDataGenerator]
          .toRight(SourceTestDataGenerationError.UnsupportedSourceError(nodeId))
        result <- createPreliminaryTestData(NonEmptyList.one(nodeId -> testDataGenerator), size)
          .toRight(SourceTestDataGenerationError.NoDataGenerated)
      } yield result
    }
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

  private def createPreliminaryTestData(
      generators: NonEmptyList[(NodeId, TestDataGenerator)],
      size: Int
  ): Option[PreliminaryScenarioTestData] = {
    val generatedData          = generateTestData(generators, size)
    val sortedRecords          = generatedData.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    val preliminaryTestRecords = sortedRecords.map(PreliminaryScenarioTestRecord.apply)
    NonEmptyList
      .fromList(preliminaryTestRecords)
      .map(PreliminaryScenarioTestData.apply)
  }

  private def prepareTestDataGenerators(
      scenario: CanonicalProcess,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[
    ScenarioTestDataGenerationError,
    NonEmptyList[(NodeId, TestDataGenerator)]
  ] = {
    commonModelDataInfoProvider
      .collectAllSources(scenario)
      .map { source =>
        val nodeId = NodeId(source.id)
        commonModelDataInfoProvider
          .compileSourceNode(source)(scenarioCompilationDependencies, nodeId)
          .leftMap { compilationErrors =>
            NonEmptyList.one(nodeId -> compilationErrors)
          }
          .map(_.cast[TestDataGenerator].map(testDataGenerator => (nodeId, testDataGenerator)))
      }
      .sequence
      .leftMap[ScenarioTestDataGenerationError](ScenarioTestDataGenerationError.ScenarioGraphValidationError)
      .toEither
      .flatMap(_.flatten.toNel.toRight(ScenarioTestDataGenerationError.NoSourcesWithTestDataGeneration))
  }

  private def generateTestData(generators: NonEmptyList[(NodeId, TestDataGenerator)], size: Int) = {
    // method TestDataGenerator.generateTestData has to be called within ModelClassLoader context
    modelData.withModelClassloaderAsContextClassLoader {
      val sourceTestDataList = generators.map { case (sourceId, testDataGenerator) =>
        val sourceTestRecords = testDataGenerator.generateTestData(size).testRecords
        sourceTestRecords.map(testRecord => ScenarioTestJsonRecord(sourceId, testRecord))
      }
      ListUtil.mergeLists(sourceTestDataList.toList, size)
    }
  }

  override def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[TestDataPreparationError, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = commonModelDataInfoProvider.collectAllSources(scenario).map(_.id).toSet
    preliminaryTestData.testRecords.zipWithIndex
      .map {
        case (PreliminaryScenarioTestRecord.Standard(sourceId, record, timestamp), _)
            if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestJsonRecord(sourceId, record, timestamp))
        case (PreliminaryScenarioTestRecord.Standard(sourceId, _, _), recordIdx) =>
          Left(TestDataPreparationError.MissingSource(NodeId(sourceId), recordIdx))
        case (PreliminaryScenarioTestRecord.Simplified(record), _) if allScenarioSourceIds.size == 1 =>
          val sourceId = allScenarioSourceIds.head
          Right(ScenarioTestJsonRecord(sourceId, record))
        case (_: PreliminaryScenarioTestRecord.Simplified, recordIdx) =>
          Left(TestDataPreparationError.MultipleSourcesRequired(recordIdx))
      }
      .sequence
      .map(scenarioTestRecords => ScenarioTestData(scenarioTestRecords.toList))
  }

}
