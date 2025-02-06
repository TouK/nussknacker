package pl.touk.nussknacker.engine.definition.test

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{TestDataGenerationError, TestDataPreparationError}
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asFragmentInputDefinition, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.ListUtil
import shapeless.syntax.typeable._

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private lazy val nodeCompiler = new NodeCompiler(
    modelData.modelDefinition,
    new FragmentParametersDefinitionExtractor(
      modelData.modelClassLoader,
      modelData.modelDefinitionWithClasses.classDefinitions.all
    ),
    expressionCompiler,
    modelData.modelClassLoader,
    Seq.empty,
    ProductionServiceInvocationCollector,
    ComponentUseCase.TestDataGeneration,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  override def getTestingCapabilities(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): TestingCapabilities = {
    val jobData = JobData(scenario.metaData, processVersion)
    collectAllSources(scenario).map(getTestingCapabilities(_, jobData)) match {
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

  private def getTestingCapabilities(source: SourceNodeData, jobData: JobData): TestingCapabilities =
    modelData.withThisAsContextClassLoader {
      val testingCapabilities = for {
        sourceObj <- compileSourceNode(source)(jobData, NodeId(source.id))
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
    modelData.withThisAsContextClassLoader {
      collectAllSources(scenario)
        .map(source => source.id -> getTestParameters(source, jobData))
        .toMap
    }
  }

  // Currently we rely on the assumption that client always call scenarioTesting / {scenarioName} / parameters endpoint
  // only when scenarioTesting / {scenarioName} / capabilities endpoint returns canTestWithForm = true. Because of that
  // for non happy-path cases we throw UnsupportedOperationException
  // TODO: This assumption is wrong. Every endpoint should be treated separately. Currently from time to time
  //       users got error notification because this endpoint is called without checking canTestWithForm = true.
  //       We can go even further and merge both endpoints
  private def getTestParameters(source: SourceNodeData, jobData: JobData): List[Parameter] =
    modelData.withThisAsContextClassLoader {
      compileSourceNode(source)(jobData, NodeId(source.id)) match {
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
  ): Either[TestDataGenerationError, PreliminaryScenarioTestData] = {
    for {
      generators <- prepareTestDataGenerators(processVersion, scenario)
      result     <- createPreliminaryTestData(generators, size)
    } yield result
  }

  def generateTestDataForSource(
      metaData: MetaData,
      sourceNodeData: SourceNodeData,
      size: Int
  ): Either[TestDataGenerationError, PreliminaryScenarioTestData] = {
    val jobData = JobData(metaData, ProcessVersion.empty)
    val nodeId  = NodeId(sourceNodeData.id)

    for {
      compiledSource <- compileSourceNode(sourceNodeData)(jobData, nodeId).toEither.left.map(errors =>
        TestDataGenerationError.SourceCompilationError(sourceNodeData.id, errors.toList)
      )
      testDataGenerator <- compiledSource
        .cast[TestDataGenerator]
        .toRight(TestDataGenerationError.UnsupportedSourceError(sourceNodeData.id))
      result <- createPreliminaryTestData(NonEmptyList.one(nodeId -> testDataGenerator), size)
    } yield result
  }

  private def createPreliminaryTestData(
      generators: NonEmptyList[(NodeId, TestDataGenerator)],
      size: Int
  ): Either[TestDataGenerationError, PreliminaryScenarioTestData] = {
    val generatedData          = generateTestData(generators, size)
    val sortedRecords          = generatedData.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    val preliminaryTestRecords = sortedRecords.map(PreliminaryScenarioTestRecord.apply)
    NonEmptyList
      .fromList(preliminaryTestRecords)
      .map(Right(_))
      .getOrElse(Left(TestDataGenerationError.NoDataGenerated))
      .map(PreliminaryScenarioTestData.apply)
  }

  private def prepareTestDataGenerators(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Either[TestDataGenerationError, NonEmptyList[(NodeId, TestDataGenerator)]] = {
    val jobData = JobData(scenario.metaData, processVersion)
    val generatorsForSourcesSupportingTestDataGeneration = for {
      source            <- collectAllSources(scenario)
      sourceObj         <- compileSourceNode(source)(jobData, NodeId(source.id)).toList
      testDataGenerator <- sourceObj.cast[TestDataGenerator]
    } yield (NodeId(source.id), testDataGenerator)
    NonEmptyList
      .fromList(generatorsForSourcesSupportingTestDataGeneration)
      .map(Right(_))
      .getOrElse(Left(TestDataGenerationError.NoSourcesWithTestDataGeneration))
  }

  private def compileSourceNode(
      source: SourceNodeData
  )(implicit jobData: JobData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, Source] = {
    nodeCompiler.compileSource(source).compiledObject
  }

  private def generateTestData(generators: NonEmptyList[(NodeId, TestDataGenerator)], size: Int) = {
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

    val allScenarioSourceIds = collectAllSources(scenario).map(_.id).toSet
    preliminaryTestData.testRecords.zipWithIndex
      .map {
        case (PreliminaryScenarioTestRecord.Standard(sourceId, record, timestamp), _)
            if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestJsonRecord(sourceId, record, timestamp))
        case (PreliminaryScenarioTestRecord.Standard(sourceId, _, _), recordIdx) =>
          Left(TestDataPreparationError.MissingSourceError(sourceId, recordIdx))
        case (PreliminaryScenarioTestRecord.Simplified(record), _) if allScenarioSourceIds.size == 1 =>
          val sourceId = allScenarioSourceIds.head
          Right(ScenarioTestJsonRecord(sourceId, record))
        case (_: PreliminaryScenarioTestRecord.Simplified, recordIdx) =>
          Left(TestDataPreparationError.MultipleSourcesError(recordIdx))
      }
      .sequence
      .map(scenarioTestRecords => ScenarioTestData(scenarioTestRecords.toList))
  }

  private def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

  private def formatError(error: String, recordIdx: Int): String = {
    s"Record ${recordIdx + 1} - $error"
  }

}
