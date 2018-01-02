package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{SourceFactory, TestDataGenerator, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.Source
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider {

  private lazy val evaluator = ExpressionEvaluator
    .withoutLazyVals(modelData.configCreator.expressionConfig(modelData.processConfig).globalProcessVariables.mapValues(_.value), List())

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData.modelClassLoader.classLoader,
    modelData.processDefinition.expressionConfig)

  override def getTestingCapabilities(metaData: MetaData, source: Source) = {
    val canTest = sourceFactory(source).flatMap[TestDataParser[_]](_.testDataParser).isDefined
    val canGenerateData = prepareTestDataGenerator(metaData, source).isDefined
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def sourceFactory(source: Source): Option[SourceFactory[_]] =
    modelData.configCreator.sourceFactories(modelData.processConfig).get(source.ref.typ).map(_.value)

  override def generateTestData(metaData: MetaData, source: Source, size: Int) =
    prepareTestDataGenerator(metaData, source).map(_.generateTestData(size))

  private def prepareTestDataGenerator(metaData: MetaData, source: Source) : Option[TestDataGenerator] = {
    implicit val meta = metaData
    implicit val nodeId = NodeId(source.id)

    for {
      factory <- sourceFactory(source)
      definition = ObjectWithMethodDef(WithCategories(factory), ProcessObjectDefinitionExtractor.source)
      sourceParams <- prepareSourceParams(definition, source)
      sourceObj = ProcessObjectFactory[process.Source[Any]](definition, evaluator).create(sourceParams)
      asTest <- sourceObj.cast[TestDataGenerator]
    } yield asTest
  }

  private def prepareSourceParams(definition: ObjectWithMethodDef, source: Source)(implicit processMetaData: MetaData, nodeId: NodeId) = {
    val parametersToCompile = source.ref.parameters
    expressionCompiler.compileObjectParameters(definition.parameters, parametersToCompile, None).toOption
  }
}