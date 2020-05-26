package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.generic.{GenericContainer, GenericData}
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.avro.{KafkaAvroSpec, TestMockSchemaRegistry}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}

class FixedKafkaAvroSinkFactorySpec extends KafkaAvroSpec with KafkaAvroSinkSpec  {

  import FixedKafkaAvroMockSchemaRegistry._

  override def schemaRegistryClient: ConfluentSchemaRegistryClient = confluentSchemaRegistryMockClient

  override protected def topics: List[(String, Int)] = List(
    (fullnameTopic, 2)
  )

  private def createSink(topic: String, avroSchemaString: String, output: LazyParameter[GenericContainer]): Unit = {
    val avroSinkFactory = new FixedKafkaAvroSinkFactory[GenericData.Record](processObjectDependencies)
    avroSinkFactory.create(metaData, topic, avroSchemaString, output)(nodeId)
  }

  test("should allow to create sink when #input schema is the same as fxied schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV1.stringSchema, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema -> #sinkSchema)") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV2.stringSchema, output)
  }

  test("should allow to create sink when #output schema is compatible with sink schema (#outputSchema <- #sinkSchema)") {
    val output = createOutput(FullNameV2.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, FullNameV1.stringSchema, output)
  }

  //TODO: Implement this
  ignore("should throw exception when #output schema doesn't match to sink schema") {
    assertThrowsWithParent[InvalidSinkOutput] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, FullNameV1.stringSchema, output)
    }
  }
}

object FixedKafkaAvroMockSchemaRegistry extends TestMockSchemaRegistry {

  val fullnameTopic: String = "fullname"

  val confluentSchemaRegistryMockClient: ConfluentSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
    .register(fullnameTopic, FullNameV1.schema, 1, isKey = false)
    .register(fullnameTopic, FullNameV1.schema, 2, isKey = false)
    .build

  val mockConfluentSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory =
    createSchemaRegistryClientFactory(confluentSchemaRegistryMockClient)
}
