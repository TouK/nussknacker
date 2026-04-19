package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.core.memory.{DataInputDeserializer, DataOutputSerializer}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

class AvroSerializersRegistrarSpec extends AnyFunSuite with Matchers {

  import AvroSerializerTestData._

  test("serializer is registered as Flink's KryoSerializer") {
    val srFactory = MockSchemaRegistryClientFactory.confluentBased(createConfluentClient())
    val srConfig  = SchemaRegistryClientKafkaConfig(Map("schema.registry.url" -> "unused"))

    val serializerConfig = new SerializerConfigImpl()
    val registrar        = new AvroSerializersRegistrar
    registrar.registerOptimizedSerializer(serializerConfig, srFactory, Map.empty[Int, SchemaRegistryClientKafkaConfig])
    registrar.addOptimizedSerializer(serializerConfig, confluentRecord1.getSchemaRegistryId, srConfig)

    val serializer = new KryoSerializer(classOf[GenericRecordWithSchemaId], serializerConfig)
    serializer.getKryo.getSerializer(classOf[GenericRecordWithSchemaId]) shouldBe a[GenericRecordWithSchemaIdSerializer]

    checkSerializationRoundTrip(serializer)
    checkSerializationRoundTrip(serializer.duplicate())
  }

  private def checkSerializationRoundTrip(serializer: KryoSerializer[GenericRecordWithSchemaId]) = {
    val output = new DataOutputSerializer(100)
    serializer.serialize(confluentRecord1, output)
    val afterRoundTrip = serializer.deserialize(new DataInputDeserializer(output.getCopyOfBuffer))
    afterRoundTrip shouldBe confluentRecord1
  }

}
