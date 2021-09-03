package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.{RunMode, SourceTestSupport}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

case class ParsedTestData(samples: List[AnyRef])

object TestDataPreparer {

  def prepareDataForTest(sourceTestSupport: SourceTestSupport[AnyRef], testData: TestData): ParsedTestData = {
    val testParserForSource = sourceTestSupport.testDataParser
    val testSamples = testParserForSource.parseTestData(testData)
    if (testSamples.size > testData.rowLimit) {
      throw new IllegalArgumentException(s"Too many samples: ${testSamples.size}, limit is: ${testData.rowLimit}")
    }
    ParsedTestData(testSamples)
  }

}

class TestDataPreparer(modelData: ModelData) {

  private val nodeCompiler = {
    val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
      case spel: SpelExpressionParser => spel.typingDictLabels
    }
    new NodeCompiler(modelData.processWithObjectsDefinition,
      expressionCompiler, modelData.modelClassLoader.classLoader, PreventInvocationCollector, RunMode.Normal)
  }

  def prepareDataForTest(espProcess: EspProcess, testData: TestData): ParsedTestData = {
    val sourceTestSupport = (espProcess.roots.map(_.data).collect {
      case e: SourceNodeData => e
    } match {
      case one :: Nil =>
        nodeCompiler.compileSource(one)(espProcess.metaData, NodeId(one.id)).compiledObject
          .fold(a => throw new IllegalArgumentException(s"Failed to compile source: ${a.toList.mkString(", ")}"), identity)
      case _ =>
        throw new IllegalArgumentException("Currently only one source can be handled")
    }) match {
      case e: SourceTestSupport[AnyRef@unchecked] => e
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser")
    }
    TestDataPreparer.prepareDataForTest(sourceTestSupport, testData)
  }

}

