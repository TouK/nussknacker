package pl.touk.nussknacker.engine.definition

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, ProcessObjectFactory}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider {

  private lazy val globalVariablesPreparer =
    GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig)

  private lazy val evaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData.modelClassLoader.classLoader,
    modelData.dictServices.dictRegistry,
    modelData.processDefinition.expressionConfig,
    modelData.processDefinition.settings)

  private lazy val factory = new ProcessObjectFactory(evaluator)

  override def getTestingCapabilities(metaData: MetaData, source: Source): TestingCapabilities = {
    val sourceObj = prepareSourceObj(source)(metaData)
    val canTest = sourceObj.exists(_.isInstanceOf[TestDataParserProvider[_]])
    val canGenerateData = sourceObj.exists(_.isInstanceOf[TestDataGenerator])
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def extractSourceFactory(source: Source): Option[ObjectWithMethodDef] = {
    modelData.processWithObjectsDefinition.sourceFactories.get(source.ref.typ)
  }

  override def generateTestData(metaData: MetaData, source: Source, size: Int): Option[Array[Byte]] =
    prepareSourceObj(source)(metaData).flatMap(_.cast[TestDataGenerator]).map(_.generateTestData(size))

  private def prepareSourceObj(source: Source)(implicit metaData: MetaData): Option[process.Source[Any]] = {
    implicit val nodeId: NodeId = NodeId(source.id)
    for {
      definition <- extractSourceFactory(source)
      sourceParams <- prepareSourceParams(definition, source)
      sourceObj <- factory.createObject[process.Source[Any]](definition, None, sourceParams).toOption
    } yield sourceObj
  }

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext = {
    val globalTypes = globalVariablesPreparer.prepareGlobalVariables(metaData).mapValues(_.typ)
    ValidationContext(Map.empty, globalTypes)
  }

  private def prepareSourceParams(definition: ObjectWithMethodDef, source: Source)
                                 (implicit processMetaData: MetaData, nodeId: NodeId) = {
    val parametersToCompile = source.ref.parameters
    val ctx = contextWithOnlyGlobalVariables
    expressionCompiler.compileObjectParameters(definition.parameters, parametersToCompile, List.empty, ctx, Map.empty, eager = false).toOption
  }
}