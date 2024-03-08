package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, InvalidPropertyFixedValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.compile.nodecompilation.{DynamicNodeValidator, TransformationResult}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.{FullNameV1, PaymentV1}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClientFactory, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData

class UniversalKafkaSinkValidationSpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._
  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = factory

  private def validate(params: (String, Expression)*): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), List.empty)
    val validator = DynamicNodeValidator(modelData)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList              = params.toList.map(p => NodeParameter(ParameterName(p._1), p._2))
    validator
      .validateNode(
        universalSinkFactory,
        paramsList,
        Nil,
        Some(VariableConstants.InputVariableName),
        Map.empty
      )(ValidationContext())
      .toOption
      .get
  }

  test("should validate specific version") {
    val result = validate(
      SinkKeyParamName.value            -> "",
      SinkValueParamName.value          -> FullNameV1.exampleData.toSpELLiteral,
      SinkRawEditorParamName.value      -> "true",
      SinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      TopicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName.value      -> "'1'"
    )

    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      SinkKeyParamName.value            -> "",
      SinkValueParamName.value          -> PaymentV1.exampleData.toSpELLiteral,
      SinkRawEditorParamName.value      -> "true",
      SinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      TopicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'"
    )

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      SinkKeyParamName.value            -> "",
      SinkValueParamName.value          -> "null",
      SinkRawEditorParamName.value      -> "true",
      SinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      TopicParamName.value              -> "'tereferer'",
      SchemaVersionParamName.value      -> "'1'"
    )

    result.errors shouldBe List(
      InvalidPropertyFixedValue(
        paramName = TopicParamName,
        label = None,
        value = "'tereferer'",
        values = List("", "'fullname'"),
        nodeId = "id"
      ),
      InvalidPropertyFixedValue(
        paramName = SchemaVersionParamName,
        label = None,
        value = "'1'",
        values = List("'latest'"),
        nodeId = "id"
      )
    )
  }

  test("should return sane error on invalid version") {
    val result = validate(
      SinkKeyParamName.value            -> "",
      SinkValueParamName.value          -> "null",
      SinkRawEditorParamName.value      -> "true",
      SinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      TopicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName.value      -> "'343543'"
    )

    result.errors shouldBe InvalidPropertyFixedValue(
      paramName = SchemaVersionParamName,
      label = None,
      value = "'343543'",
      values = List("'latest'", "'1'", "'2'", "'3'"),
      nodeId = "id"
    ) :: Nil
  }

  test("should validate value") {
    val result = validate(
      SinkKeyParamName.value            -> "",
      SinkValueParamName.value          -> "''",
      SinkRawEditorParamName.value      -> "true",
      SinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      TopicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'"
    )

    result.errors shouldBe CustomNodeError(
      "id",
      "Provided value does not match scenario output - errors:\nIncorrect type: actual: 'String()' expected: 'Record{id: String, amount: Double, currency: EnumSymbol[PLN | EUR | GBP | USD] | String, company: Record{name: String, address: Record{street: String, city: String}}, products: List[Record{id: String, name: String, price: Double}], vat: Integer | Null}'.",
      Some(SinkValueParamName)
    ) :: Nil
  }

}
