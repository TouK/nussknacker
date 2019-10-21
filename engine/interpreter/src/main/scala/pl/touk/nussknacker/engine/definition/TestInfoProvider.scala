package pl.touk.nussknacker.engine.definition

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{SourceFactory, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.{MetaData, process}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.variables.MetaVariables
import shapeless.syntax.typeable._

trait TestInfoProvider {

  def getTestingCapabilities(metaData: MetaData, source: Source) : TestingCapabilities

  def generateTestData(metaData: MetaData, source: Source, size: Int) : Option[Array[Byte]]

}

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean)

class ModelDataTestInfoProvider(modelData: ModelData) extends TestInfoProvider {

  private lazy val evaluator = ExpressionEvaluator
    .withoutLazyVals(modelData.configCreator.expressionConfig(modelData.processConfig).globalProcessVariables.mapValues(_.value), List())

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData.modelClassLoader.classLoader,
    modelData.processDefinition.expressionConfig)

  private lazy val factory = new ProcessObjectFactory(evaluator)

  override def getTestingCapabilities(metaData: MetaData, source: Source): TestingCapabilities = {
    val sourceObj = prepareSourceObj(source)(metaData)
    val canTest = sourceObj.exists(_.isInstanceOf[TestDataParserProvider[_]])
    val canGenerateData = sourceObj.exists(_.isInstanceOf[TestDataGenerator])
    TestingCapabilities(canBeTested = canTest, canGenerateTestData = canGenerateData)
  }

  private def extractSourceFactory(source: Source): Option[SourceFactory[_]] = {
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
      sourceFactory <- extractSourceFactory(source)
      definition = ObjectWithMethodDef.withEmptyConfig(sourceFactory, ProcessObjectDefinitionExtractor.source)
      sourceParams <- prepareSourceParams(definition, source)
      sourceObj = factory.create[process.Source[Any]](definition, sourceParams, None)
    } yield sourceObj
  }

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext = {
    val globalTypes = modelData.processDefinition.expressionConfig.globalVariables.mapValues(_.returnType)
    val typesWithMeta = MetaVariables.withType(globalTypes)
    ValidationContext(Map.empty, typesWithMeta)
  }

  private def prepareSourceParams(definition: ObjectWithMethodDef, source: Source)
                                 (implicit processMetaData: MetaData, nodeId: NodeId) = {
    val parametersToCompile = source.ref.parameters
    val ctx = contextWithOnlyGlobalVariables
    expressionCompiler.compileObjectParameters(definition.parameters, parametersToCompile, List.empty, ctx.clearVariables, ctx).toOption
  }
}