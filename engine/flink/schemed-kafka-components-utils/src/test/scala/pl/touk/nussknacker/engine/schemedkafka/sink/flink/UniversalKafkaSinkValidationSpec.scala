package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.scalatest.OptionValues.convertOptionToValuable
import pl.touk.nussknacker.engine.ModelConfig.JsonLikeValuesEnteringMode
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  EmptyMandatoryParameter,
  ExpressionParserCompilationError,
  InvalidPropertyFixedValue
}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{
  EngineScenarioCompilationDependencies,
  FixedExpressionValue,
  JsonTemplateParameterEditor,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext.LiveRuntime
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  DynamicNodeValidator,
  SingleInputNodeInputValidationContext,
  TransformationResult
}
import pl.touk.nussknacker.engine.definition.component.NodeCompilationDependencies
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.{FullNameV1, PaymentV1}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

class UniversalKafkaSinkValidationSpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import pl.touk.nussknacker.test.LiteralSpELImplicits._

  import KafkaAvroSinkMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = factory

  private lazy val defaultUniversalSinkFactory: UniversalKafkaSinkFactory =
    universalSinkFactory(jsonLikeValuesEnteringMode = JsonLikeValuesEnteringMode.DynamicForms)

  private lazy val universalKafkaSinkFactoryWithTemplateParam: UniversalKafkaSinkFactory =
    universalSinkFactory(jsonLikeValuesEnteringMode = JsonLikeValuesEnteringMode.SingleJsonTemplateParameter)

  private implicit val sinkNodeId: NodeId = NodeId("id")

  private def validate(
      params: (ParameterName, Expression)*
  )(universalSinkFactory: UniversalKafkaSinkFactory): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), List.empty)
    val validator = DynamicNodeValidator(modelData)
    val metaData  = MetaData("processId", StreamMetaData())
    val jobData   = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val nodeCompilationDependencies = new NodeCompilationDependencies(
      scenarioCompilationDependencies =
        new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty),
      nodeData = Source("id", SourceRef("typ", params.toList.map(p => NodeParameter(p._1, p._2)))),
      componentUseContext = LiveRuntime(None)
    )
    validator
      .validateNode(
        compilationDependencies = nodeCompilationDependencies,
        component = universalSinkFactory,
        parametersConfig = Map.empty,
        nodeInputValidationContext = SingleInputNodeInputValidationContext(ValidationContext.empty)
      )
      .toOption
      .get
  }

  test("should validate specific version") {
    val result = validate(
      topicParamName              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName      -> "'1'".spel,
      sinkKeyParamName            -> "".spel,
      sinkRawEditorParamName      -> "true".spel,
      sinkValidationModeParamName -> validationModeParam(ValidationMode.strict),
      sinkValueParamName          -> FullNameV1.exampleData.toSpELLiteral.spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe Nil
  }

  test("should validate nested record") {
    val result = validate(
      topicParamName             -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName     -> "'4'".spel,
      sinkKeyParamName           -> "".spel,
      sinkRawEditorParamName     -> "false".spel,
      ParameterName("mapSimple") -> """{id:{id:"10"}}""".spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe Nil
  }

  test("sink validation succeeds for non-nullable AVRO enum value from string expression") {
    val result = validateKafkaSinkUsingAvroEnumTopic(schemaVersion = 1, testFieldValue = "'GREEN'".spel)
    result.errors shouldBe Nil
  }

  test("sink validation succeeds for null value in nullable AVRO enum") {
    val result = validateKafkaSinkUsingAvroEnumTopic(schemaVersion = 2, testFieldValue = "null".spel)
    result.errors shouldBe Nil
  }

  test("sink validation succeeds for union field with AVRO enum and matching alternative type") {
    val result = validateKafkaSinkUsingAvroEnumTopic(schemaVersion = 3, testFieldValue = "123".spel)
    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      topicParamName              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName      -> s"'3'".spel,
      sinkKeyParamName            -> "".spel,
      sinkRawEditorParamName      -> "true".spel,
      sinkValidationModeParamName -> validationModeParam(ValidationMode.strict),
      sinkValueParamName          -> PaymentV1.exampleData.toSpELLiteral.spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      topicParamName              -> "'tereferer'".spel,
      schemaVersionParamName      -> "'1'".spel,
      sinkKeyParamName            -> "".spel,
      sinkRawEditorParamName      -> "true".spel,
      sinkValidationModeParamName -> validationModeParam(ValidationMode.strict),
      sinkValueParamName          -> "null".spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe List(
      InvalidPropertyFixedValue(
        paramName = topicParamName,
        label = None,
        value = "'tereferer'",
        values = ("" :: allRegisteredTopics).map(fixedExpressionValue),
        nodeId = sinkNodeId.id
      ),
      InvalidPropertyFixedValue(
        paramName = schemaVersionParamName,
        label = None,
        value = "'1'",
        values = List(FixedExpressionValue(s"'latest'", "Latest version")),
        nodeId = sinkNodeId.id
      )
    )
  }

  test("should return sane error on invalid version") {
    val result = validate(
      topicParamName              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName      -> "'343543'".spel,
      sinkKeyParamName            -> "".spel,
      sinkRawEditorParamName      -> "true".spel,
      sinkValidationModeParamName -> validationModeParam(ValidationMode.strict),
      sinkValueParamName          -> "null".spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe InvalidPropertyFixedValue(
      paramName = schemaVersionParamName,
      label = None,
      value = "'343543'",
      values =
        List(FixedExpressionValue(s"'latest'", "Latest version")) ++ List("1", "2", "3", "4").map(fixedExpressionValue),
      nodeId = sinkNodeId.id
    ) :: Nil
  }

  test("should validate value") {
    val result = validate(
      topicParamName              -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'".spel,
      schemaVersionParamName      -> s"'3'".spel,
      sinkKeyParamName            -> "".spel,
      sinkRawEditorParamName      -> "true".spel,
      sinkValidationModeParamName -> validationModeParam(ValidationMode.strict),
      sinkValueParamName          -> "''".spel,
    )(defaultUniversalSinkFactory)

    result.errors shouldBe CustomNodeError(
      sinkNodeId.id,
      "Provided value does not match scenario output - errors:\nIncorrect type: actual: 'String()' expected: 'Record{id: String, amount: Double, currency: EnumSymbol[PLN | EUR | GBP | USD] | String, company: Record{name: String, address: Record{street: String, city: String}}, products: List[Record{id: String, name: String, price: Double}], vat: Integer | Null}'.",
      Some(sinkValueParamName)
    ) :: Nil
  }

  test("sink should be validated when the topic is empty, and a minimal list of parameters should be returned") {
    val result = validate()(universalKafkaSinkFactoryWithTemplateParam)

    result.parameters.map(_.name) shouldBe List(topicParamName, schemaVersionParamName, sinkKeyParamName)
    result.errors shouldBe List(
      EmptyMandatoryParameter(
        "Field: Topic is mandatory and can not be empty",
        "Please fill field for this parameter",
        topicParamName,
        sinkNodeId.id
      )
    )
  }

  test("sink validation fails for invalid AVRO enum value with dynamic forms") {
    expectErrorWhenUsingInvalidAvroEnumValue(
      testFieldValue = "'INVALID_COLOR'".spel,
      expectedErrorMessage =
        "Errors:\nIncorrect value: actual value: 'INVALID_COLOR' should be one of: ['RED', 'GREEN', 'BLUE']."
    )

  }

  test("sink validation fails for null value in non-nullable AVRO enum with dynamic forms") {
    expectErrorWhenUsingInvalidAvroEnumValue(
      testFieldValue = "null".spel,
      expectedErrorMessage =
        "Errors:\nIncorrect value: actual value: 'null' should be one of: ['RED', 'GREEN', 'BLUE']."
    )
  }

  test("sink validation fails for other value in non-nullable AVRO enum with dynamic forms") {
    val result = validateKafkaSinkUsingAvroEnumTopic(schemaVersion = 1, testFieldValue = "123".spel)
    result.errors shouldBe ExpressionParserCompilationError(
      message = "Bad expression type, expected: EnumSymbol | String, found: Integer(123)",
      nodeId = sinkNodeId.id,
      paramName = Some(ParameterName("testField")),
      originalExpr = "123",
      details = Some(CoordinatesBasedTextRange(TextCoordinates(0, 0), TextCoordinates(3, 0)))
    ) :: Nil
  }

  test("sink should be validated against the topic using an AVRO schema, with default values applied from the schema") {
    val topicName     = KafkaAvroSinkMockSchemaRegistry.exampleAvroTopic
    val schemaVersion = 1
    val expectedDefaultValue =
      Expression.jsonTemplate(
        s"""{
           |  "stringField" : "example",
           |  "intField" : 42,
           |  "longField" : 1234567890,
           |  "floatField" : 3.14,
           |  "doubleField" : 2.71828,
           |  "booleanField" : true,
           |  "nullField" : null,
           |  "enumField" : "BLUE",
           |  "arrayField" : [
           |    ""
           |  ],
           |  "mapField" : {
           |    
           |  },
           |  "fixedField" : "\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000",
           |  "bytesField" : "",
           |  "unionField" : null,
           |  "recordField" : {
           |    "nestedString" : ""
           |  },
           |  "logicalDate" : 0,
           |  "logicalTimestamp" : 1680000000000
           |}""".stripMargin
      )

    val result = validate(
      topicParamName         -> s"'$topicName'".spel,
      schemaVersionParamName -> s"'$schemaVersion'".spel
    )(universalKafkaSinkFactoryWithTemplateParam)

    result.parameters.map(_.name) shouldBe List(
      topicParamName,
      schemaVersionParamName,
      sinkKeyParamName,
      sinkValidationModeParamName,
      sinkValueParamName
    )

    val sinkValueParameter = result.parameters.find(_.name == sinkValueParamName).value
    sinkValueParameter.editors shouldBe List(JsonTemplateParameterEditor, SpelParameterEditor)
    sinkValueParameter.defaultValue shouldBe Some(expectedDefaultValue)

    val laxValidationResult = validate(
      topicParamName              -> s"'$topicName'".spel,
      schemaVersionParamName      -> s"'$schemaVersion'".spel,
      sinkKeyParamName            -> "".spel,
      sinkValidationModeParamName -> "'lax'".spel,
      sinkValueParamName          -> expectedDefaultValue
    )(universalKafkaSinkFactoryWithTemplateParam)

    laxValidationResult.errors shouldBe List(
      CustomNodeError(
        "id",
        "Provided value does not match scenario output - errors:\nIncorrect type: path 'bytesField' actual: 'String' expected: 'ByteBuffer'.",
        Some(sinkValueParamName)
      )
    )
  }

  test(
    "sink should be validated against the topic using an AVRO schema, with default values generated from the schema"
  ) {
    val topicName     = KafkaAvroSinkMockSchemaRegistry.exampleAvroTopic
    val schemaVersion = 2
    val expectedDefaultValue =
      Expression.jsonTemplate(
        s"""{
           |  "stringField" : "",
           |  "intField" : 0,
           |  "longField" : 0,
           |  "floatField" : 0.0,
           |  "doubleField" : 0.0,
           |  "booleanField" : false,
           |  "nullField" : null,
           |  "enumField" : "RED",
           |  "arrayField" : [
           |    ""
           |  ],
           |  "mapField" : {
           |    
           |  },
           |  "fixedField" : "\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000",
           |  "bytesField" : "",
           |  "unionField" : null,
           |  "recordField" : {
           |    "nestedString" : ""
           |  },
           |  "logicalDate" : 0,
           |  "logicalTimestamp" : 0
           |}""".stripMargin
      )

    val result = validate(
      topicParamName         -> s"'$topicName'".spel,
      schemaVersionParamName -> s"'$schemaVersion'".spel
    )(universalKafkaSinkFactoryWithTemplateParam)

    result.parameters.map(_.name) shouldBe List(
      topicParamName,
      schemaVersionParamName,
      sinkKeyParamName,
      sinkValidationModeParamName,
      sinkValueParamName
    )

    val sinkValueParameter = result.parameters.find(_.name == sinkValueParamName).value
    sinkValueParameter.editors shouldBe List(JsonTemplateParameterEditor, SpelParameterEditor)
    sinkValueParameter.defaultValue shouldBe Some(expectedDefaultValue)

    result.errors shouldBe List(
      CustomNodeError(
        "id",
        "Provided value does not match scenario output - errors:\nIncorrect type: path 'bytesField' actual: 'String' expected: 'ByteBuffer', path 'logicalTimestamp' actual: 'Integer' expected: 'Instant | Long'.",
        Some(sinkValueParamName)
      )
    )

    val laxValidationResult = validate(
      topicParamName              -> s"'$topicName'".spel,
      schemaVersionParamName      -> s"'$schemaVersion'".spel,
      sinkKeyParamName            -> "".spel,
      sinkValidationModeParamName -> "'lax'".spel,
      sinkValueParamName          -> expectedDefaultValue
    )(universalKafkaSinkFactoryWithTemplateParam)

    laxValidationResult.errors shouldBe List(
      CustomNodeError(
        "id",
        "Provided value does not match scenario output - errors:\nIncorrect type: path 'bytesField' actual: 'String' expected: 'ByteBuffer', path 'logicalTimestamp' actual: 'Integer' expected: 'Instant | Long'.",
        Some(sinkValueParamName)
      )
    )

  }

  test("sink should be validated against the topic using a JSON schema, with default values applied from the schema") {
    val topicName     = KafkaAvroSinkMockSchemaRegistry.exampleJsonTopic
    val schemaVersion = 1
    val expectedDefaultValue =
      Expression.jsonTemplate(
        s"""{
           |  "arrayField" : [
           |    "item1",
           |    "item2"
           |  ],
           |  "booleanField" : true,
           |  "bytesField" : "AQID",
           |  "doubleField" : 2.71828,
           |  "enumField" : "RED",
           |  "fixedField" : "AAAAAAAAAAAAAAAAAAAAAA==",
           |  "floatField" : 3.14,
           |  "intField" : 42,
           |  "logicalDate" : "2022-01-01",
           |  "logicalTimestamp" : "2023-03-28T12:00:00Z",
           |  "longField" : 1234567890,
           |  "mapField" : {
           |    "a" : 1,
           |    "b" : 2
           |  },
           |  "nullField" : null,
           |  "recordField" : {
           |    "nestedString" : "inside"
           |  },
           |  "stringField" : "example",
           |  "unionField" : null
           |}""".stripMargin
      )
    val result = validate(
      topicParamName         -> s"'$topicName'".spel,
      schemaVersionParamName -> s"'$schemaVersion'".spel
    )(universalKafkaSinkFactoryWithTemplateParam)
    result.parameters.map(_.name) shouldBe List(
      topicParamName,
      schemaVersionParamName,
      sinkKeyParamName,
      sinkValidationModeParamName,
      sinkValueParamName
    )
    result.errors shouldBe CustomNodeError(
      sinkNodeId.id,
      "Provided value does not match scenario output - errors:\nIncorrect type: path 'logicalDate' actual: 'String' expected: 'LocalDate | LocalDate', path 'logicalTimestamp' actual: 'String' expected: 'ZonedDateTime | LocalDateTime'.",
      Some(sinkValueParamName)
    ) :: Nil

    val sinkValueParameter = result.parameters.find(_.name == sinkValueParamName).value
    sinkValueParameter.editors shouldBe List(JsonTemplateParameterEditor, SpelParameterEditor)
    sinkValueParameter.defaultValue shouldBe Some(expectedDefaultValue)

    val laxValidationResult = validate(
      topicParamName              -> s"'$topicName'".spel,
      schemaVersionParamName      -> s"'$schemaVersion'".spel,
      sinkKeyParamName            -> "".spel,
      sinkValidationModeParamName -> "'lax'".spel,
      sinkValueParamName          -> expectedDefaultValue
    )(universalKafkaSinkFactoryWithTemplateParam)

    laxValidationResult.errors shouldBe List(
      CustomNodeError(
        "id",
        "Provided value does not match scenario output - errors:\nIncorrect type: path 'logicalDate' actual: 'String' expected: 'LocalDate | LocalDate', path 'logicalTimestamp' actual: 'String' expected: 'ZonedDateTime | LocalDateTime'.",
        Some(sinkValueParamName)
      )
    )
  }

  test(
    "sink should be validated against the topic using a JSON schema, with default values generated from the schema"
  ) {
    val topicName     = KafkaAvroSinkMockSchemaRegistry.exampleJsonTopic
    val schemaVersion = 2
    val expectedDefaultValue =
      Expression.jsonTemplate(
        s"""{
           |  "arrayField" : [
           |    ""
           |  ],
           |  "booleanField" : false,
           |  "bytesField" : "",
           |  "doubleField" : 0.0,
           |  "enumField" : "RED",
           |  "fixedField" : "",
           |  "floatField" : 0.0,
           |  "intField" : 0,
           |  "logicalDate" : "",
           |  "logicalTimestamp" : "",
           |  "longField" : 0,
           |  "mapField" : {
           |    
           |  },
           |  "nullField" : null,
           |  "recordField" : {
           |    "nestedString" : ""
           |  },
           |  "stringField" : "",
           |  "unionField" : ""
           |}""".stripMargin
      )
    val result = validate(
      topicParamName         -> s"'$topicName'".spel,
      schemaVersionParamName -> s"'$schemaVersion'".spel
    )(universalKafkaSinkFactoryWithTemplateParam)
    result.parameters.map(_.name) shouldBe List(
      topicParamName,
      schemaVersionParamName,
      sinkKeyParamName,
      sinkValidationModeParamName,
      sinkValueParamName
    )
    val sinkValueParameter = result.parameters.find(_.name == sinkValueParamName).value
    sinkValueParameter.editors shouldBe List(JsonTemplateParameterEditor, SpelParameterEditor)
    sinkValueParameter.defaultValue shouldBe Some(expectedDefaultValue)

    result.errors shouldBe CustomNodeError(
      sinkNodeId.id,
      "Provided value does not match scenario output - errors:\nIncorrect type: path 'logicalDate' actual: 'String' expected: 'LocalDate | LocalDate', path 'logicalTimestamp' actual: 'String' expected: 'ZonedDateTime | LocalDateTime'.",
      Some(sinkValueParamName)
    ) :: Nil

    val laxValidationResult = validate(
      topicParamName              -> s"'$topicName'".spel,
      schemaVersionParamName      -> s"'$schemaVersion'".spel,
      sinkKeyParamName            -> "".spel,
      sinkValidationModeParamName -> "'lax'".spel,
      sinkValueParamName          -> expectedDefaultValue
    )(universalKafkaSinkFactoryWithTemplateParam)

    laxValidationResult.errors shouldBe List(
      CustomNodeError(
        "id",
        "Provided value does not match scenario output - errors:\nIncorrect type: path 'logicalDate' actual: 'String' expected: 'LocalDate | LocalDate', path 'logicalTimestamp' actual: 'String' expected: 'ZonedDateTime | LocalDateTime'.",
        Some(sinkValueParamName)
      )
    )

  }

  test("sink should be validated against the topic without schema") {
    val topicName = "example-topic-without-schema"
    kafkaClient.createTopic(topicName)

    val expectedDefaultValue =
      Expression.jsonTemplate(
        s"""{
           |  
           |}""".stripMargin
      )
    val result = validate(
      topicParamName -> s"'$topicName'".spel,
    )(universalKafkaSinkFactoryWithTemplateParam)

    result.parameters.map(_.name) shouldBe List(
      topicParamName,
      contentTypeParamName,
      sinkKeyParamName,
      sinkValueParamName
    )

    val sinkValueParameter = result.parameters.find(_.name == sinkValueParamName).value
    sinkValueParameter.editors shouldBe List(JsonTemplateParameterEditor, SpelParameterEditor)
    sinkValueParameter.defaultValue shouldBe Some(expectedDefaultValue)

    result.errors shouldBe List.empty

    val validationForSinkDefaultValue = validate(
      topicParamName       -> s"'$topicName'".spel,
      contentTypeParamName -> s"'JSON'".spel,
      sinkKeyParamName     -> "".spel,
      sinkValueParamName   -> expectedDefaultValue
    )(universalKafkaSinkFactoryWithTemplateParam)

    validationForSinkDefaultValue.errors shouldBe List.empty

  }

  private def expectErrorWhenUsingInvalidAvroEnumValue(testFieldValue: Expression, expectedErrorMessage: String) = {
    val result = validateKafkaSinkUsingAvroEnumTopic(schemaVersion = 1, testFieldValue = testFieldValue)
    result.errors shouldBe CustomNodeError(
      nodeId = sinkNodeId.id,
      message = expectedErrorMessage,
      paramName = Some(ParameterName("testField"))
    ) :: Nil
  }

  private def validateKafkaSinkUsingAvroEnumTopic(schemaVersion: Int, testFieldValue: Expression) = {
    val topic = exampleAvroEnumTopic
    validate(
      topicParamName             -> s"'$topic'".spel,
      schemaVersionParamName     -> s"'$schemaVersion'".spel,
      sinkKeyParamName           -> "".spel,
      sinkRawEditorParamName     -> "false".spel,
      ParameterName("testField") -> testFieldValue,
    )(defaultUniversalSinkFactory)
  }

  private def fixedExpressionValue(value: String) = {
    if (value == "") FixedExpressionValue("", "")
    else FixedExpressionValue(s"'$value'", value)
  }

}
