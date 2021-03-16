package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, typing}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer._
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, GeneratedAvroClassWithLogicalTypes, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class KafkaAvroSinkFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private def validate(params: (String, Expression)*): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)
    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
      modelData.processWithObjectsDefinition.expressionConfig)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSinkFactory, paramsList, Nil, Some(Interpreter.InputParamName))(ValidationContext()).toOption.get
  }

  protected def createSink(topic: String, versionOption: SchemaVersionOption, value: LazyParameter[GenericContainer], validationMode: ValidationMode): Sink = {
    val version = versionOption match {
      case LatestSchemaVersion => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(version) => version.toString
    }
    avroSinkFactory.implementation(
      Map(KafkaAvroBaseTransformer.TopicParamName -> topic,
        KafkaAvroBaseTransformer.SchemaVersionParamName -> version,
        KafkaAvroBaseTransformer.SinkKeyParamName -> null,
        KafkaAvroBaseTransformer.SinkValidationModeParameterName -> validationMode.name,
        KafkaAvroBaseTransformer.SinkValueParamName -> value),
      List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)), None)
  }

  test("should throw exception when schema doesn't exist") {
    assertThrows[CustomNodeValidationException] {
      val valueParam = createLazyParam(FullNameV1.schema, FullNameV1.exampleData)
      createSink("not-exists-subject", ExistingSchemaVersion(1), valueParam, ValidationMode.strict)
    }
  }

  test("should throw exception when schema version doesn't exist") {
    assertThrows[CustomNodeValidationException] {
      val valueParam = createLazyParam(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, ExistingSchemaVersion(666), valueParam, ValidationMode.strict)
    }
  }

  test("should allow to create sink when #value schema is the same as sink schema") {
    val valueParam = createLazyParam(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, ExistingSchemaVersion(1), valueParam, ValidationMode.strict)
  }

  test("should allow to create sink when #value schema is compatible with newer sink schema") {
    val valueParam = createLazyParam(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, ExistingSchemaVersion(2), valueParam, ValidationMode.allowOptional)
  }

  test("should allow to create sink when #value schema is compatible with older fixed sink schema") {
    val valueParam = createLazyParam(FullNameV2.schema, FullNameV2.exampleData)
    createSink(fullnameTopic, ExistingSchemaVersion(1), valueParam, ValidationMode.allowRedundantAndOptional)
  }

  test("should throw exception when #value schema is not compatible with sink schema") {
    assertThrows[CustomNodeValidationException] {
      val valueParam = createLazyParam(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, ExistingSchemaVersion(2), valueParam, ValidationMode.strict)
    }
  }

  test("should handle incompatible schemas for specific records") {
    val ex = intercept[CustomNodeValidationException] {
      val valueParam = new LazyParameter[GenericContainer] {
        override def returnType: typing.TypingResult = Typed[GeneratedAvroClassWithLogicalTypes]
      }
      createSink(generatedAvroTopic, ExistingSchemaVersion(generatedNewSchemaVersion), valueParam, ValidationMode.strict)
    }
    ex.getMessage shouldEqual "Provided value does not match selected Avro schema - errors:\nNone of the following types:\n" +
      " - {text: CharSequence, dateTime: Instant, decimal: BigDecimal, date: LocalDate, time: LocalTime}\n" +
      "can be a subclass of any of:\n" +
      " - {text2: CharSequence}"
  }

  test("should validate specific version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> "'tereferer'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Fetching schema error for topic: tereferer, version: ExistingSchemaVersion(1)", Some(TopicParamName)) :: Nil
  }

  test("should return sane error on invalid version") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "'343543'")

    result.errors shouldBe CustomNodeError("id", "Fetching schema error for topic: fullname, version: ExistingSchemaVersion(343543)", Some(SchemaVersionParamName)) :: Nil
  }

  test("should validate value") {
    val result = validate(
      SinkKeyParamName -> "",
      SinkValueParamName -> "''",
      SinkValidationModeParameterName -> validationModeParam(ValidationMode.strict),
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe CustomNodeError("id",
      "Provided value does not match selected Avro schema - errors:\nNone of the following types:\n - String\ncan be a subclass of any of:\n - {vat: Integer, products: List[{id: CharSequence, name: CharSequence, price: Double} | {id: CharSequence, name: CharSequence, price: Double}], amount: Double, company: {name: CharSequence, address: {street: CharSequence, city: CharSequence} | {street: CharSequence, city: CharSequence}} | {name: CharSequence, address: {street: CharSequence, city: CharSequence} | {street: CharSequence, city: CharSequence}}, id: CharSequence, currency: EnumSymbol | CharSequence}",
      Some(SinkValueParamName)) :: Nil
  }

}
