package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SourceTestSupport}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

case class ParsedTestData[T](samples: List[T])

object TestDataPreparer {

  def prepareDataForTest[T](sourceTestSupport: SourceTestSupport[T], scenarioTestData: ScenarioTestData): ParsedTestData[T] = {
    val testParserForSource = sourceTestSupport.testRecordParser
    val testSamples = scenarioTestData.testRecords.map(scenarioTestRecord => testParserForSource.parse(scenarioTestRecord.record))
    ParsedTestData(testSamples)
  }

  def prepareDataForTest[T](sourceTestSupport: SourceTestSupport[T], scenarioTestData: ScenarioTestData, sourceId: NodeId): ParsedTestData[T] = {
    val testParserForSource = sourceTestSupport.testRecordParser
    val testSamples = scenarioTestData.testRecords
      .filter(_.sourceId == sourceId)
      .map(scenarioTestRecord => testParserForSource.parse(scenarioTestRecord.record))
    ParsedTestData(testSamples)
  }

}

class TestDataPreparer(modelData: ModelData) {

  private val nodeCompiler = {
    val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
      case spel: SpelExpressionParser => spel.typingDictLabels
    }
    new NodeCompiler(modelData.processWithObjectsDefinition,
      expressionCompiler, modelData.modelClassLoader.classLoader, PreventInvocationCollector, ComponentUseCase.TestDataGeneration)
  }

  def prepareDataForTest[T](scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): ParsedTestData[T] = modelData.withThisAsContextClassLoader {
    val sourceTestSupport = (scenario.allStartNodes.map(_.head.data).collect {
      case e: SourceNodeData => e
    } match {
      case one :: Nil =>
        nodeCompiler.compileSource(one)(scenario.metaData, NodeId(one.id)).compiledObject
          .fold(a => throw new IllegalArgumentException(s"Failed to compile source: ${a.toList.mkString(", ")}"), identity)
      case _ =>
        throw new IllegalArgumentException("Currently only one source can be handled")
    }) match {
      case e: SourceTestSupport[T@unchecked] => e
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser")
    }
    TestDataPreparer.prepareDataForTest(sourceTestSupport, scenarioTestData)
  }

}

