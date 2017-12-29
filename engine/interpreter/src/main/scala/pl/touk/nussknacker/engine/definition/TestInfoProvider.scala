package pl.touk.nussknacker.engine.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ExpressionEvaluator
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, SourceFactory, TestDataGenerator, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, PartSubGraphCompiler, PartSubGraphCompilerBase}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

trait ConfigCreatorTestInfoProvider extends TestInfoProvider {

//FIXME: Two below methods names are confusing
  def configCreator: ProcessConfigCreator

  def processConfig: Config

  def processDefinition: ProcessDefinition[ObjectDefinition]

  def modelClassLoader : ModelClassLoader

  //FIXME: should it be here??
  private lazy val evaluator = ExpressionEvaluator
    .withoutLazyVals(configCreator.expressionConfig(processConfig).globalProcessVariables.mapValues(_.value), List())

  //FIXME???
  private lazy val expressionCompiler = ExpressionCompiler.default(modelClassLoader.classLoader, processDefinition.expressionConfig, false)

  override def getTestingCapabilities(metaData: MetaData, source: Source) = {
    val canTest = sourceFactory(source).flatMap[TestDataParser[_]](_.testDataParser).isDefined
    val canGenerateData = prepareTestDataGenerator(metaData, source).isDefined
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def sourceFactory(source: Source): Option[SourceFactory[_]] =
    configCreator.sourceFactories(processConfig).get(source.ref.typ).map(_.value)

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
    //FXIME
    val parametersToCompile = source.ref.parameters.map(p => evaluatedparam.Parameter(p.name, Expression("spel", p.value)))
    expressionCompiler.compileObjectParameters(definition.parameters, parametersToCompile, None).toOption
  }
}