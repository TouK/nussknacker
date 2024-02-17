package pl.touk.nussknacker.engine.definition.test

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{
  ComponentUseCase,
  SourceTestSupport,
  TestDataGenerator,
  TestWithParametersSupport
}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asFragmentInputDefinition, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.util.ListUtil
import shapeless.syntax.typeable._

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private lazy val nodeCompiler = new NodeCompiler(
    modelData.modelDefinition,
    new FragmentParametersDefinitionExtractor(modelData.modelClassLoader.classLoader),
    expressionCompiler,
    modelData.modelClassLoader.classLoader,
    Seq.empty,
    ProductionServiceInvocationCollector,
    ComponentUseCase.TestDataGeneration,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  override def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities = {
    collectAllSources(scenario).map(getTestingCapabilities(_, scenario.metaData)) match {
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

  private def getTestingCapabilities(source: SourceNodeData, metaData: MetaData): TestingCapabilities =
    modelData.withThisAsContextClassLoader {
      val testingCapabilities = for {
        sourceObj <- prepareSourceObj(source)(metaData, NodeId(source.id))
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

  override def getTestParameters(scenario: CanonicalProcess): Map[String, List[Parameter]] =
    modelData.withThisAsContextClassLoader {
      collectAllSources(scenario)
        .map(source => source.id -> getTestParameters(source, scenario.metaData))
        .toMap
    }

  private def getTestParameters(source: SourceNodeData, metaData: MetaData): List[Parameter] =
    modelData.withThisAsContextClassLoader {
      prepareSourceObj(source)(metaData, NodeId(source.id)) match {
        case Some(s: TestWithParametersSupport[_]) => s.testParametersDefinition
        case _ =>
          throw new UnsupportedOperationException(
            s"Requested test parameters from source (${source.id}) that does not implement TestWithParametersSupport."
          )
      }
    }

  override def generateTestData(scenario: CanonicalProcess, size: Int): Option[PreliminaryScenarioTestData] = {
    val sourceTestDataGenerators = prepareTestDataGenerators(scenario)
    val sourceTestDataList = sourceTestDataGenerators.map { case (sourceId, testDataGenerator) =>
      val sourceTestRecords = testDataGenerator.generateTestData(size).testRecords
      sourceTestRecords.map(testRecord => ScenarioTestJsonRecord(sourceId, testRecord))
    }
    val scenarioTestRecords = ListUtil.mergeLists(sourceTestDataList, size)
    // Records without timestamp are put at the end of the list.
    val sortedRecords          = scenarioTestRecords.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    val preliminaryTestRecords = sortedRecords.map(PreliminaryScenarioTestRecord.apply)
    Some(preliminaryTestRecords).filter(_.nonEmpty).map(PreliminaryScenarioTestData)
  }

  private def prepareTestDataGenerators(scenario: CanonicalProcess): List[(NodeId, TestDataGenerator)] = {
    for {
      source            <- collectAllSources(scenario)
      sourceObj         <- prepareSourceObj(source)(scenario.metaData, NodeId(source.id))
      testDataGenerator <- sourceObj.cast[TestDataGenerator]
    } yield (NodeId(source.id), testDataGenerator)
  }

  private def prepareSourceObj(
      source: SourceNodeData
  )(implicit metaData: MetaData, nodeId: NodeId): Option[process.Source] = {
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

  override def prepareTestData(
      preliminaryTestData: PreliminaryScenarioTestData,
      scenario: CanonicalProcess
  ): Either[String, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = collectAllSources(scenario).map(_.id).toSet
    preliminaryTestData.testRecords.zipWithIndex
      .map {
        case (PreliminaryScenarioTestRecord.Standard(sourceId, record, timestamp), _)
            if allScenarioSourceIds.contains(sourceId) =>
          Right(ScenarioTestJsonRecord(sourceId, record, timestamp))
        case (PreliminaryScenarioTestRecord.Standard(sourceId, _, _), recordIdx) =>
          Left(formatError(s"scenario does not have source id: '$sourceId'", recordIdx))
        case (PreliminaryScenarioTestRecord.Simplified(record), _) if allScenarioSourceIds.size == 1 =>
          val sourceId = allScenarioSourceIds.head
          Right(ScenarioTestJsonRecord(sourceId, record))
        case (_: PreliminaryScenarioTestRecord.Simplified, recordIdx) =>
          Left(formatError("scenario has multiple sources but got record without source id", recordIdx))
      }
      .sequence
      .map(scenarioTestRecords => ScenarioTestData(scenarioTestRecords))
  }

  private def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

  private def formatError(error: String, recordIdx: Int): String = {
    s"Record ${recordIdx + 1} - $error"
  }

}
