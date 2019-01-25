package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{SourceFactory, TestDataGenerator, TestDataParserProvider, WithCategories}
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

  override def getTestingCapabilities(metaData: MetaData, source: Source): TestingCapabilities = {
    val sourceObj = prepareSourceObj(source)(metaData)
    val canTest = sourceObj.exists(_.isInstanceOf[TestDataParserProvider[_]])
    val canGenerateData = sourceObj.exists(_.isInstanceOf[TestDataGenerator])
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def sourceFactory(source: Source): Option[SourceFactory[_]] = {
    // this wrapping with model class loader is necessary because during sourceFactories creation, some classes
    // can be not loaded yet - for example dynamically loaded AvroKryoSerializerUtils (from flink-avro library)
    modelData.withThisAsContextClassLoader {
      modelData.configCreator.sourceFactories(modelData.processConfig).get(source.ref.typ).map(_.value)
    }
  }

  override def generateTestData(metaData: MetaData, source: Source, size: Int): Option[Array[Byte]] =
    prepareSourceObj(source)(metaData).flatMap(_.cast[TestDataGenerator]).map(_.generateTestData(size))

  private def prepareSourceObj(source: Source)(implicit metaData: MetaData): Option[process.Source[Any]] = {
    implicit val nodeId: NodeId = NodeId(source.id)
    for {
      factory <- sourceFactory(source)
      definition = ObjectWithMethodDef.withEmptyConfig(factory, ProcessObjectDefinitionExtractor.source)
      sourceParams <- prepareSourceParams(definition, source)
      sourceObj = ProcessObjectFactory[process.Source[Any]](definition, evaluator).create(sourceParams)
    } yield sourceObj
  }

  private def prepareSourceParams(definition: ObjectWithMethodDef, source: Source)
                                 (implicit processMetaData: MetaData, nodeId: NodeId) = {
    val parametersToCompile = source.ref.parameters
    expressionCompiler.compileObjectParameters(definition.parameters, parametersToCompile, None).toOption
  }
}