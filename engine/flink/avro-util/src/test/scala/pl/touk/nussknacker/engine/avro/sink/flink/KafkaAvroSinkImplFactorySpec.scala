package pl.touk.nussknacker.engine.avro.sink.flink

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, InvalidPropertyFixedValue, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class KafkaAvroSinkImplFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private def validate(params: (String, Expression)*): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)
    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
      modelData.processWithObjectsDefinition.expressionConfig)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSinkFactory, paramsList, Nil, Some(VariableConstants.InputVariableName), SingleComponentConfig.zero)(ValidationContext()).toOption.get
  }

  test("should validate specific version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "null",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "null",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "null",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> "'tereferer'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe InvalidPropertyFixedValue(TopicParamName, None, "'tereferer'", List("", "'fullname'", "'generated-avro'"), "id") ::
      InvalidPropertyFixedValue(SchemaVersionParamName, None, "'1'", List("'latest'"), "id") :: Nil
  }

  test("should return sane error on invalid version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "null",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "'343543'")

    result.errors shouldBe InvalidPropertyFixedValue(SchemaVersionParamName, None, "'343543'", List("'latest'", "'1'", "'2'", "'3'"), "id") :: Nil
  }

  test("should validate value") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "''",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe CustomNodeError("id",
      "Provided value does not match selected Avro schema - errors:\nNone of the following types:\n - String\ncan be a subclass of any of:\n - {id: String, amount: Double, currency: EnumSymbol | String, company: {name: String, address: {street: String, city: String} | {street: String, city: String}} | {name: String, address: {street: String, city: String} | {street: String, city: String}}, products: List[{id: String, name: String, price: Double} | {id: String, name: String, price: Double}], vat: Integer}",
      Some(SinkValueParamName)) :: Nil
  }

}
