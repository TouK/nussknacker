package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceTestSupport, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.node.{Source, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import shapeless.syntax.typeable._


trait TestInfoProvider {

  def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities

  def generateTestData(scenario: CanonicalProcess, size: Int): Option[ScenarioTestData]

}

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

object TestingCapabilities {
  val Disabled: TestingCapabilities = TestingCapabilities(canBeTested = false, canGenerateTestData = false)
}

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
    case spel: SpelExpressionParser => spel.typingDictLabels
  }

  private lazy val nodeCompiler = new NodeCompiler(modelData.processWithObjectsDefinition, expressionCompiler, modelData.modelClassLoader.classLoader, ProductionServiceInvocationCollector, ComponentUseCase.TestDataGeneration)

  override def getTestingCapabilities(scenario: CanonicalProcess): TestingCapabilities = {
    collectAllSources(scenario)
      .map(getTestingCapabilities(_, scenario.metaData))
      .foldLeft(TestingCapabilities.Disabled)((tc1, tc2) => TestingCapabilities(
        canBeTested = tc1.canBeTested || tc2.canBeTested,
        canGenerateTestData = tc1.canGenerateTestData || tc2.canGenerateTestData
      ))
  }

  private def getTestingCapabilities(source: Source, metaData: MetaData): TestingCapabilities = modelData.withThisAsContextClassLoader {
    val testingCapabilities = for {
      sourceObj <- prepareSourceObj(source)(metaData)
      canTest = sourceObj.isInstanceOf[SourceTestSupport[_]]
      canGenerateData = sourceObj.isInstanceOf[TestDataGenerator]
    } yield TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
    testingCapabilities.getOrElse(TestingCapabilities.Disabled)
  }

  override def generateTestData(scenario: CanonicalProcess, size: Int): Option[ScenarioTestData] = {
    for {
      _ <- Some(size) if size > 0
      sourceTestDataGenerators = prepareTestDataGenerators(scenario) if sourceTestDataGenerators.nonEmpty
      sourceTestDataSizes = divideEvenlyIntoParts(size, partsCount = sourceTestDataGenerators.size)
      scenarioTestRecords = sourceTestDataGenerators.zip(sourceTestDataSizes).flatMap { case ((sourceId, testDataGenerator), testDataSize) =>
        val sourceTestRecords = testDataGenerator.generateTestData(testDataSize).testRecords
        sourceTestRecords.map(testRecord => ScenarioTestRecord(sourceId, testRecord))
      } if scenarioTestRecords.nonEmpty
      // Records without timestamp are at the end of the list.
      sortedRecords = scenarioTestRecords.sortBy(_.record.timestamp.getOrElse(Long.MaxValue))
    } yield ScenarioTestData(sortedRecords)
  }

  private def prepareTestDataGenerators(scenario: CanonicalProcess): List[(NodeId, TestDataGenerator)] = {
    for {
      source <- collectAllSources(scenario)
      sourceObj <- prepareSourceObj(source)(scenario.metaData)
      testDataGenerator <- sourceObj.cast[TestDataGenerator]
    } yield (NodeId(source.id), testDataGenerator)
  }

  private def collectAllSources(scenario: CanonicalProcess): List[Source] = {
    scenario.collectAllNodes.flatMap(asSource)
  }

  private def prepareSourceObj(source: Source)(implicit metaData: MetaData): Option[process.Source] = {
    implicit val nodeId: NodeId = NodeId(source.id)
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

  private def divideEvenlyIntoParts(n: Int, partsCount: Int): List[Int] = {
    require(n > 0)
    require(partsCount > 0)

    val partSizes = List.fill(partsCount)(n / partsCount)
    val partSizeRemainders = List.fill(n % partsCount)(1)
    partSizes.zipAll(partSizeRemainders, 0, 0).map { case (partSize, partRemainder) => partSize + partRemainder }
  }

}
