package pl.touk.nussknacker.engine.avro.source

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{AvroStringSettings, FullNameV1, FullNameV2, GeneratedAvroClassWithLogicalTypes, GeneratedAvroClassWithLogicalTypesNewSchema, PaymentDate, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.api.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneOffset}
import scala.collection.immutable.ListMap

class KafkaAvroPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  test("should read generated generic record in v1 with null key") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), null, givenObj)
  }

  test("should read generated generic record in v1 with empty string key") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read generated specific record in v1") {
    val givenObj = FullNameV1.createSpecificRecord("Jan", "Kowalski")

    roundTripKeyValueObject(specificSourceFactory[FullNameV1], useStringForKey = true, RecordTopic, ExistingSchemaVersion(1), null, givenObj)
  }

  test("should read last generated generic record with logical types") {
    val givenObj = PaymentDate.record

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, PaymentDateTopic, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read last generated specific record with logical types ") {
    val date = LocalDateTime.of(2020, 1, 2, 3, 14, 15)
    val decimal = new java.math.BigDecimal("12.34")
    val givenObj = new GeneratedAvroClassWithLogicalTypes("loremipsum", date.toInstant(ZoneOffset.UTC), date.toLocalDate, date.toLocalTime, decimal)

    roundTripKeyValueObject(specificSourceFactory[GeneratedAvroClassWithLogicalTypes], useStringForKey = true, GeneratedWithLogicalTypesTopic, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read generated record in v2") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, ExistingSchemaVersion(2), null, givenObj)
  }

  test("should read generated record in last version") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, RecordTopic, LatestSchemaVersion, null, givenObj)
  }

  test("should return validation errors when schema doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), "fake-topic", ExistingSchemaVersion(1), null, givenObj) should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(_, "Fetching schema error for topic: fake-topic, version: ExistingSchemaVersion(1)", _), _)) => ()
    }
  }

  test("should return validation errors when schema version doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), RecordTopic, ExistingSchemaVersion(3), null, givenObj) should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(_, "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(3)", _), _)) => ()
    }
  }

  test("should read last generated simple object without key schema") {
    val givenObj = 123123

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, IntTopicNoKey, ExistingSchemaVersion(1), null, givenObj)
  }

  test("should ignore key schema and empty key value with string-as-key deserialization") {
    val givenObj = 123123

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, IntTopicWithKey, ExistingSchemaVersion(1), null, givenObj)
  }

  test("should read last generated simple object with expected key schema and valid key") {
    val givenObj = 123123

    val serializedKey = keySerializer.serialize(IntTopicWithKey, -1)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey, givenObj)
    kafkaClient.sendRawMessage(IntTopicWithKey, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), IntTopicWithKey, ExistingSchemaVersion(1), new String(serializedKey, StandardCharsets.UTF_8), givenObj)
  }

  test("should read object with invalid defaults") {
    val givenObj = new GenericData.Record(InvalidDefaultsSchema)
    givenObj.put("field1", "foo")

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = true, InvalidDefaultsTopic, ExistingSchemaVersion(1), null, givenObj)
  }

  test("should read last generated key-value object, simple type") {
    val givenKey = 123
    val givenValue = 456

    val serializedKey = keySerializer.serialize(IntTopicWithKey, givenKey)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey, givenValue)
    kafkaClient.sendRawMessage(IntTopicWithKey, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = false, IntTopicWithKey, ExistingSchemaVersion(1), givenKey, givenValue)
  }

  test("should read last generated key-value object, complex object") {
    val givenKey = FullNameV1.record
    val givenValue = PaymentV1.record

    val serializedKey = keySerializer.serialize(RecordTopicWithKey, givenKey)
    val serializedValue = valueSerializer.serialize(RecordTopicWithKey, givenValue)
    kafkaClient.sendRawMessage(RecordTopicWithKey, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(avroSourceFactory, useStringForKey = false, RecordTopicWithKey, ExistingSchemaVersion(1), givenKey, givenValue)
  }

  test("Should validate specific version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe Nil
  }

  test("Should validate latest version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
  }

  test("Should return sane error on invalid topic") {
    val result = validate(TopicParamName -> "'terefere'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe
      CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Fetching schema error for topic: terefere, version: LatestSchemaVersion", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown, VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)))
  }

  test("Should return sane error on invalid version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "'12345'")

    result.errors shouldBe CustomNodeError("id", "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(12345)", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown, VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)))
  }

  test("Should properly detect input type") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
    result.outputContext shouldBe ValidationContext(
      Map(
        VariableConstants.InputVariableName -> TypedObjectTypingResult(
          ListMap(
            "first" -> AvroStringSettings.stringTypingResult,
            "middle" -> AvroStringSettings.stringTypingResult,
            "last" -> AvroStringSettings.stringTypingResult
          ),
          Typed.typedClass[GenericRecord]
        ),
        VariableConstants.InputMetaVariableName -> InputMeta.withType(Typed[String])
      )
    )
  }

  private def validate(params: (String, Expression)*): TransformationResult = {

    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
      modelData.processWithObjectsDefinition.expressionConfig)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSourceFactory(useStringForKey = true), paramsList, Nil, Some(VariableConstants.InputVariableName))(ValidationContext()).toOption.get
  }

}
