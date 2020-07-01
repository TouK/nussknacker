package pl.touk.nussknacker.engine.avro.sink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSpecMixin}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaSubjectNotFound, SchemaVersionNotFound}

class KafkaAvroSinkFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val avroSinkFactory: KafkaAvroSinkFactory = {
    val schemaRegistryProvider = createSchemaRegistryProvider[Any](useSpecificAvroReader = false)
    new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies)
  }

  protected def createSink(topic: String, version: Integer, output: LazyParameter[GenericContainer]): Sink =
    avroSinkFactory.implementation(
      Map(KafkaAvroFactory.TopicParamName -> topic,
          KafkaAvroFactory.SchemaVersionParamName -> version, KafkaAvroFactory.SinkOutputParamName -> output),
            List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)))

  test("should throw exception when schema doesn't exist") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink("not-exists-subject", 1, output)
    }
  }

  test("should throw exception when schema version doesn't exist") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, 666, output)
    }
  }

  test("should allow to create sink when #output schema is the same as sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should allow to create sink when #output schema is compatible with newer sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 2, output)
  }

  test("should allow to create sink when #output schema is compatible with older fxied sink schema") {
    val output = createOutput(FullNameV2.schema, FullNameV2.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should throw exception when #output schema is not compatible with sink schema") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }
}
