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
import pl.touk.nussknacker.engine.spel.SpelExtension._
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
      sinkKeyParamName.value            -> "".spel,
      sinkValueParamName.value          -> FullNameV1.exampleData.toSpELLiteral.spel,
      sinkRawEditorParamName.value      -> "true".spel,
      sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      topicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName.value      -> "'1'".spel
    )

    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      sinkKeyParamName.value            -> "".spel,
      sinkValueParamName.value          -> PaymentV1.exampleData.toSpELLiteral.spel,
      sinkRawEditorParamName.value      -> "true".spel,
      sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      topicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      sinkKeyParamName.value            -> "".spel,
      sinkValueParamName.value          -> "null".spel,
      sinkRawEditorParamName.value      -> "true".spel,
      sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      topicParamName.value              -> "'tereferer'".spel,
      schemaVersionParamName.value      -> "'1'".spel
    )

    result.errors shouldBe List(
      InvalidPropertyFixedValue(
        paramName = topicParamName,
        label = None,
        value = "'tereferer'",
        values = List("", "'fullname'"),
        nodeId = "id"
      ),
      InvalidPropertyFixedValue(
        paramName = schemaVersionParamName,
        label = None,
        value = "'1'",
        values = List("'latest'"),
        nodeId = "id"
      )
    )
  }

  test("should return sane error on invalid version") {
    val result = validate(
      sinkKeyParamName.value            -> "".spel,
      sinkValueParamName.value          -> "null".spel,
      sinkRawEditorParamName.value      -> "true".spel,
      sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      topicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName.value      -> "'343543'".spel
    )

    result.errors shouldBe InvalidPropertyFixedValue(
      paramName = schemaVersionParamName,
      label = None,
      value = "'343543'",
      values = List("'latest'", "'1'", "'2'", "'3'"),
      nodeId = "id"
    ) :: Nil
  }

  test("should validate value") {
    val result = validate(
      sinkKeyParamName.value            -> "".spel,
      sinkValueParamName.value          -> "''".spel,
      sinkRawEditorParamName.value      -> "true".spel,
      sinkValidationModeParamName.value -> validationModeParam(ValidationMode.strict),
      topicParamName.value              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName.value      -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )

    result.errors shouldBe CustomNodeError(
      "id",
      "Provided value does not match scenario output - errors:\nIncorrect type: actual: 'String()' expected: 'Record{id: String, amount: Double, currency: EnumSymbol[PLN | EUR | GBP | USD] | String, company: Record{name: String, address: Record{street: String, city: String}}, products: List[Record{id: String, name: String, price: Double}], vat: Integer | Null}'.",
      Some(sinkValueParamName)
    ) :: Nil
  }

}
