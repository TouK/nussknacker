package pl.touk.nussknacker.engine.definition.test

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceTestSupport, TestDataGenerator, TestViewGenerator}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.node.{Source, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.util.ListUtil
import shapeless.syntax.typeable._

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
    case spel: SpelExpressionParser => spel.typingDictLabels
  }

  private lazy val nodeCompiler = new NodeCompiler(modelData.processWithObjectsDefinition, modelData.subprocessDefinitionExtractor, expressionCompiler, modelData.modelClassLoader.classLoader, ProductionServiceInvocationCollector, ComponentUseCase.TestDataGeneration)

  override def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities = {
    collectAllSources(scenario)
      .map(getTestingCapabilities(_, scenario.metaData))
      .foldLeft(TestingCapabilities.Disabled)((tc1, tc2) => TestingCapabilities(
        canBeTested = tc1.canBeTested || tc2.canBeTested,
        canGenerateTestData = tc1.canGenerateTestData || tc2.canGenerateTestData,
        canCreateTestView = tc1.canCreateTestView || tc2.canCreateTestView,
      ))
  }

  private def getTestingCapabilities(source: Source, metaData: MetaData): TestingCapabilities = modelData.withThisAsContextClassLoader {
    val testingCapabilities = for {
      sourceObj <- prepareSourceObj(source)(metaData)
      canTest = sourceObj.isInstanceOf[SourceTestSupport[_]]
      canGenerateData = sourceObj.isInstanceOf[TestDataGenerator]
      canCreateTestView = sourceObj.isInstanceOf[TestViewGenerator]
    } yield TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData, canCreateTestView = canCreateTestView)
    testingCapabilities.getOrElse(TestingCapabilities.Disabled)
  }

  override def generateTestData(scenario: CanonicalProcess, size: Int): Option[PreliminaryScenarioTestData] = {
    val sourceTestDataGenerators = prepareTestDataGenerators(scenario)
    val sourceTestDataList = sourceTestDataGenerators.map { case (sourceId, testDataGenerator) =>
      val sourceTestRecords = testDataGenerator.generateTestData(size).testRecords
      sourceTestRecords.map(testRecord => ScenarioTestRecord(sourceId, testRecord))
    }
    val scenarioTestRecords = ListUtil.mergeLists(sourceTestDataList, size)
    // Records without timestamp are put at the end of the list.
    val sortedRecords = scenarioTestRecords.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    val preliminaryTestRecords = sortedRecords.map(PreliminaryScenarioTestRecord.apply)
    Some(preliminaryTestRecords).filter(_.nonEmpty).map(PreliminaryScenarioTestData)
  }

  private def prepareTestDataGenerators(scenario: CanonicalProcess): List[(NodeId, TestDataGenerator)] = {
    for {
      source <- collectAllSources(scenario)
      sourceObj <- prepareSourceObj(source)(scenario.metaData)
      testDataGenerator <- sourceObj.cast[TestDataGenerator]
    } yield (NodeId(source.id), testDataGenerator)
  }

  private def prepareSourceObj(source: Source)(implicit metaData: MetaData): Option[process.Source] = {
    implicit val nodeId: NodeId = NodeId(source.id)
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

  override def prepareTestData(preliminaryTestData: PreliminaryScenarioTestData, scenario: CanonicalProcess): Either[String, ScenarioTestData] = {
    import cats.implicits._

    val allScenarioSourceIds = collectAllSources(scenario).map(_.id).toSet
    preliminaryTestData.testRecords.zipWithIndex.map {
      case (PreliminaryScenarioTestRecord.Standard(sourceId, record, timestamp), _) if allScenarioSourceIds.contains(sourceId) =>
        Right(ScenarioTestRecord(sourceId, record, timestamp))
      case (PreliminaryScenarioTestRecord.Standard(sourceId, _, _), recordIdx) =>
        Left(formatError(s"scenario does not have source id: '$sourceId'", recordIdx))
      case (PreliminaryScenarioTestRecord.Simplified(record), _) if allScenarioSourceIds.size == 1 =>
        val sourceId = allScenarioSourceIds.head
        Right(ScenarioTestRecord(sourceId, record))
      case (_: PreliminaryScenarioTestRecord.Simplified, recordIdx) =>
        Left(formatError("scenario has multiple sources but got record without source id", recordIdx))
    }.sequence.map(ScenarioTestData)
  }

  private def collectAllSources(scenario: CanonicalProcess): List[Source] = {
    scenario.collectAllNodes.flatMap(asSource)
  }

  private def formatError(error: String, recordIdx: Int): String = {
    s"Record ${recordIdx + 1} - $error"
  }

}
