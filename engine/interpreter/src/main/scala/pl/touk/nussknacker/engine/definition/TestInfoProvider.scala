package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.{Interpreter, ModelData}
import pl.touk.nussknacker.engine.api.process.{TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, ProcessObjectFactory}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider with LazyLogging {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
    case spel: SpelExpressionParser => spel.typingDictLabels
  }

  private lazy val nodeCompiler = new NodeCompiler(modelData.processWithObjectsDefinition, expressionCompiler, modelData.modelClassLoader.classLoader, None)

  override def getTestingCapabilities(metaData: MetaData, source: Source): TestingCapabilities = {
    val sourceObj = prepareSourceObj(source)(metaData)
    val canTest = sourceObj.exists(_.isInstanceOf[TestDataParserProvider[_]])
    val canGenerateData = sourceObj.exists(_.isInstanceOf[TestDataGenerator])
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  override def generateTestData(metaData: MetaData, source: Source, size: Int): Option[Array[Byte]] =
    prepareSourceObj(source)(metaData).flatMap(_.cast[TestDataGenerator]).map(_.generateTestData(size))

  private def prepareSourceObj(source: Source)(implicit metaData: MetaData): Option[process.Source[Any]] = {
    implicit val nodeId: NodeId = NodeId(source.id)
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

}