package pl.touk.nussknacker.engine.schemedkafka.source.flink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, InvalidPropertyFixedValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.compile.nodecompilation.{DynamicNodeValidator, TransformationResult}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  ExistingSchemaVersion,
  LatestSchemaVersion,
  SchemaRegistryClientFactory,
  SchemaVersionOption
}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

class KafkaAvroPayloadSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = factory

  test("should read generated generic record in v1 with null key") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(1),
      null,
      givenObj
    )
  }

  test("should read generated generic record in v1 with empty string key") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(1),
      "",
      givenObj
    )
  }

  test("should read last generated generic record with logical types") {
    val givenObj = PaymentDate.record

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      PaymentDateTopic.name,
      ExistingSchemaVersion(1),
      "",
      givenObj
    )
  }

  test("should read generated record in v2") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      ExistingSchemaVersion(2),
      null,
      givenObj
    )
  }

  test("should read generated record in last version") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      RecordTopic.name,
      LatestSchemaVersion,
      null,
      givenObj
    )
  }

  test("should return validation errors when schema doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(
      universalSourceFactory(useStringForKey = true),
      "fake-topic",
      ExistingSchemaVersion(1),
      null,
      givenObj
    ) should matchPattern {
      case Invalid(
            NonEmptyList(
              CustomNodeError(_, "Fetching schema error for topic: fake-topic, version: ExistingSchemaVersion(1)", _),
              _
            )
          ) =>
        ()
    }
  }

  test("should return validation errors when schema version doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(
      universalSourceFactory(useStringForKey = true),
      RecordTopic.name,
      ExistingSchemaVersion(3),
      null,
      givenObj
    ) should matchPattern {
      case Invalid(
            NonEmptyList(
              CustomNodeError(
                _,
                "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(3)",
                _
              ),
              _
            )
          ) =>
        ()
    }
  }

  test("should read last generated simple object without key schema") {
    val givenObj = 123123

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      IntTopicNoKey.name,
      ExistingSchemaVersion(1),
      null,
      givenObj
    )
  }

  test("should ignore key schema and empty key value with string-as-key deserialization") {
    val givenObj = 123123

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      IntTopicWithKey.name,
      ExistingSchemaVersion(1),
      null,
      givenObj
    )
  }

  test("should read last generated simple object with expected key schema and valid key") {
    val givenObj = 123123

    val serializedKey   = keySerializer.serialize(IntTopicWithKey.name, -1)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey.name, givenObj)
    kafkaClient.sendRawMessage(IntTopicWithKey.name, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(
      universalSourceFactory(useStringForKey = true),
      IntTopicWithKey.name,
      ExistingSchemaVersion(1),
      new String(serializedKey, StandardCharsets.UTF_8),
      givenObj
    )
  }

  test("should read object with invalid defaults") {
    val givenObj = new GenericData.Record(InvalidDefaultsSchema)
    givenObj.put("field1", "foo")

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = true,
      InvalidDefaultsTopic.name,
      ExistingSchemaVersion(1),
      null,
      givenObj
    )
  }

  test("should read array of primitives on top level") {
    val topic        = ArrayOfNumbersTopic
    val arrayOfInts  = List(123).asJava
    val arrayOfLongs = List(123L).asJava
    val wrappedObj   = new NonRecordContainer(ArrayOfIntsSchema, arrayOfInts)
    pushMessageWithKey(null, wrappedObj, topic.name, useStringForKey = true)

    readLastMessageAndVerify(
      universalSourceFactory(useStringForKey = true),
      topic.name,
      ExistingSchemaVersion(2),
      null,
      arrayOfLongs
    )
  }

  test("should read array of records on top level") {
    val topic            = ArrayOfRecordsTopic
    val recordV1         = FullNameV1.createRecord("Jan", "Kowalski")
    val arrayOfRecordsV1 = List(recordV1).asJava
    val recordV2         = FullNameV2.createRecord("Jan", null, "Kowalski")
    val arrayOfRecordsV2 = List(recordV2).asJava
    val wrappedObj       = new NonRecordContainer(ArrayOfRecordsV1Schema, arrayOfRecordsV1)
    pushMessageWithKey(null, wrappedObj, topic.name, useStringForKey = true)

    readLastMessageAndVerify(
      universalSourceFactory(useStringForKey = true),
      topic.name,
      ExistingSchemaVersion(2),
      null,
      arrayOfRecordsV2
    )
  }

  test("should read last generated key-value object, simple type") {
    val givenKey   = 123
    val givenValue = 456

    val serializedKey   = keySerializer.serialize(IntTopicWithKey.name, givenKey)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey.name, givenValue)
    kafkaClient.sendRawMessage(IntTopicWithKey.name, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = false,
      IntTopicWithKey.name,
      ExistingSchemaVersion(1),
      givenKey,
      givenValue
    )
  }

  test("should read last generated key-value object, complex object") {
    val givenKey   = FullNameV1.record
    val givenValue = PaymentV1.record

    val serializedKey   = keySerializer.serialize(RecordTopicWithKey.name, givenKey)
    val serializedValue = valueSerializer.serialize(RecordTopicWithKey.name, givenValue)
    kafkaClient.sendRawMessage(RecordTopicWithKey.name, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(
      universalSourceFactory,
      useStringForKey = false,
      RecordTopicWithKey.name,
      ExistingSchemaVersion(1),
      givenKey,
      givenValue
    )
  }

  test("Should validate specific version") {
    val result =
      validate(
        topicParamName.value         -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic.name}'".spel,
        schemaVersionParamName.value -> "'1'".spel
      )

    result.errors shouldBe Nil
  }

  test("Should validate latest version") {
    val result = validate(
      topicParamName.value         -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )

    result.errors shouldBe Nil
  }

  test("Should return sane error on invalid topic") {
    val result =
      validate(
        topicParamName.value         -> "'terefere'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )

    result.errors shouldBe
      InvalidPropertyFixedValue(
        topicParamName,
        None,
        "'terefere'",
        List(
          "",
          "'testArrayOfNumbersTopic'",
          "'testArrayOfRecordsTopic'",
          "'testAvroIntTopic1NoKey'",
          "'testAvroIntTopic1WithKey'",
          "'testAvroInvalidDefaultsTopic1'",
          "'testAvroRecordTopic1'",
          "'testAvroRecordTopic1WithKey'",
          "'testPaymentDateTopic'"
        ),
        "id"
      ) :: Nil
    result.outputContext shouldBe ValidationContext(
      Map(
        VariableConstants.InputVariableName     -> Unknown,
        VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)
      )
    )
  }

  test("Should return sane error on invalid version") {
    val result = validate(
      topicParamName.value         -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic.name}'".spel,
      schemaVersionParamName.value -> "'12345'".spel
    )

    result.errors shouldBe InvalidPropertyFixedValue(
      paramName = schemaVersionParamName,
      label = None,
      value = "'12345'",
      values = List("'latest'", "'1'", "'2'"),
      nodeId = "id"
    ) :: Nil
    result.outputContext shouldBe ValidationContext(
      Map(
        VariableConstants.InputVariableName     -> Unknown,
        VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)
      )
    )
  }

  test("Should properly detect input type") {
    val result = validate(
      topicParamName.value         -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic.name}'".spel,
      schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )

    result.errors shouldBe Nil
    result.outputContext shouldBe ValidationContext(
      Map(
        VariableConstants.InputVariableName -> Typed.record(
          ListMap(
            "first"  -> Typed[String],
            "middle" -> Typed[String],
            "last"   -> Typed[String]
          ),
          Typed.typedClass[GenericRecord]
        ),
        VariableConstants.InputMetaVariableName -> InputMeta.withType(Typed[String])
      )
    )
  }

  private def validate(params: (String, Expression)*): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), List.empty)
    val validator = DynamicNodeValidator(modelData)
    val metaData  = MetaData("processId", StreamMetaData())

    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    implicit val nodeId: NodeId   = NodeId("id")
    val paramsList                = params.toList.map(p => NodeParameter(ParameterName(p._1), p._2))
    validator
      .validateNode(
        universalSourceFactory(useStringForKey = true),
        paramsList,
        Nil,
        Some(VariableConstants.InputVariableName),
        Map.empty
      )(ValidationContext())
      .toOption
      .get
  }

}
