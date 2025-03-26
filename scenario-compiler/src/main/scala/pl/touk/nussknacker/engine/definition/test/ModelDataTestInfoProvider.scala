package pl.touk.nussknacker.engine.definition.test

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.EngineNodeDependencies
import pl.touk.nussknacker.engine.definition.action.CommonModelDataInfoProvider
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{ScenarioTestDataGenerationError, SourceTestDataGenerationError, TestDataPreparationError}
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.util.ListUtil
import pl.touk.nussknacker.engine.{JobRuntimeData, ModelData}
import shapeless.syntax.typeable._

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {
  private val commonModelDataInfoProvider = new CommonModelDataInfoProvider(modelData)

  override def getTestingCapabilities(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): TestingCapabilities = {
    val jobData = JobData(scenario.metaData, processVersion)
    // FIXME abr
    val jobRuntimeData = new JobRuntimeData(jobData, EngineNodeDependencies.empty)
    commonModelDataInfoProvider.collectAllSources(scenario).map(getTestingCapabilities(_, jobRuntimeData)) match {
      case Nil => TestingCapabilities.Disabled
      case s =>
        s.reduce((tc1, tc2) =>
          TestingCapabilities(
            canBeTested = tc1.canBeTested || tc2.canBeTested,
            canGenerateTestData = tc1.canGenerateTestData || tc2.canGenerateTestData,
            canTestWithForm =
              tc1.canTestWithForm && tc2.canTestWithForm, // TODO change to "or" after adding support for multiple sources
          )
        )
    }
  }

  private def getTestingCapabilities(source: SourceNodeData, jobRuntimeData: JobRuntimeData): TestingCapabilities = {
    val testingCapabilities = for {
      sourceObj <- commonModelDataInfoProvider.compileSourceNode(source)(jobRuntimeData, NodeId(source.id))
      canTest         = sourceObj.isInstanceOf[SourceTestSupport[_]]
      canGenerateData = sourceObj.isInstanceOf[TestDataGenerator]
      canTestWithForm = sourceObj.isInstanceOf[TestWithParametersSupport[_]]
    } yield TestingCapabilities(
      canBeTested = canTest,
      canGenerateTestData = canGenerateData,
      canTestWithForm = canTestWithForm
    )
    testingCapabilities.getOrElse(TestingCapabilities.Disabled)
  }

  override def getTestParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Map[String, List[Parameter]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    // FIXME abr
    val jobRuntimeData = new JobRuntimeData(jobData, EngineNodeDependencies.empty)
    commonModelDataInfoProvider
      .collectAllSources(scenario)
      .map(source => source.id -> getTestParametersWithDefaults(source, jobRuntimeData))
      .toMap

  }

  private def getTestParametersWithDefaults(source: SourceNodeData, jobRuntimeData: JobRuntimeData): List[Parameter] = {
    val testParameters = getTestParameters(source, jobRuntimeData)
    val testParametersEnrichedWithDefaults =
      StandardParameterEnrichment.enrichParameterDefinitions(testParameters, Map.empty)
    testParametersEnrichedWithDefaults
  }

  // Currently we rely on the assumption that client always call scenarioTesting / {scenarioName} / parameters endpoint
  // only when scenarioTesting / {scenarioName} / capabilities endpoint returns canTestWithForm = true. Because of that
  // for non happy-path cases we throw UnsupportedOperationException
  // TODO: This assumption is wrong. Every endpoint should be treated separately. Currently from time to time
  //       users got error notification because this endpoint is called without checking canTestWithForm = true.
  //       We can go even further and merge both endpoints
  private def getTestParameters(source: SourceNodeData, jobRuntimeData: JobRuntimeData): List[Parameter] = {
    commonModelDataInfoProvider.compileSourceNode(source)(jobRuntimeData, NodeId(source.id)) match {
      case Valid(s: TestWithParametersSupport[_]) => s.testParametersDefinition
      case Valid(sourceWithoutTestWithParametersSupport) =>
        throw new UnsupportedOperationException(
          s"Requested test parameters from source [${source.id}] of [${sourceWithoutTestWithParametersSupport.getClass.getName}] class that does not implement TestWithParametersSupport."
        )
      case Invalid(errors) =>
        throw new UnsupportedOperationException(
          s"Requested test parameters from source [${source.id}] that is not valid. Errors: ${errors.toList.mkString(", ")}"
        )

    }
  }

  override def generateTestData(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess,
      size: Int
  ): Either[ScenarioTestDataGenerationError, PreliminaryScenarioTestData] = {
    val jobData = JobData(scenario.metaData, processVersion)
    // FIXME abr
    val jobRuntimeData = new JobRuntimeData(jobData, EngineNodeDependencies.empty)
    for {
      generators <- prepareTestDataGenerators(scenario, jobRuntimeData)
      result <- createPreliminaryTestData(generators, size)
        .toRight(ScenarioTestDataGenerationError.NoDataGenerated)
    } yield result
  }

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[SourceTestDataGenerationError, PreliminaryScenarioTestData] = {
    val jobData = JobData(metaData, ProcessVersion.empty)
    // FIXME abr
    val jobRuntimeData = new JobRuntimeData(jobData, EngineNodeDependencies.empty)
    val nodeId         = NodeId(sourceNodeData.id)
    for {
      compiledSource <- commonModelDataInfoProvider
        .compileSourceNode(sourceNodeData)(jobRuntimeData, nodeId)
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
      jobRuntimeData: JobRuntimeData
  ): Either[
    ScenarioTestDataGenerationError,
    NonEmptyList[(NodeId, TestDataGenerator)]
  ] = {
    commonModelDataInfoProvider
      .collectAllSources(scenario)
      .map { source =>
        val nodeId = NodeId(source.id)
        commonModelDataInfoProvider
          .compileSourceNode(source)(jobRuntimeData, nodeId)
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
    modelData.withThisAsContextClassLoader {
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
