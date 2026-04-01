package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import com.typesafe.config.ConfigFactory
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.kafka.{SchemaRegistryCacheConfig, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.{
  AvroSerializersRegistrar,
  GenericRecordWithSchemaIdSerializer
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class AvroBenchmark {

  private val avroKryoTypeInfo = TypeInformation.of(classOf[GenericData.Record])

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(
    avroKryoTypeInfo,
    AvroSamples.sampleRecord,
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config)
  )

  private[avro] val schemaIdBasedKryoSetup = new SerializationBenchmarkSetup(
    avroKryoTypeInfo,
    AvroSamples.sampleRecordWithSchemaId,
    config => {
      val schemaRegistryMockClient = new MockSchemaRegistryClient
      val parsedSchema             = ConfluentUtils.convertToAvroSchema(AvroSamples.sampleSchema, Some(1))
      schemaRegistryMockClient.register("foo-value", parsedSchema, 1, AvroSamples.sampleSchemaId.asInt)
      val factory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

      new AvroSerializersRegistrar().register(ConfigFactory.empty(), config)
      GenericRecordWithSchemaIdSerializer.register(
        AvroSamples.sampleSchemaRegistryId,
        factory.create(
          SchemaRegistryClientKafkaConfig(Map("bootstrap.servers" -> "noKafka"), SchemaRegistryCacheConfig(), None)
        )
      )

      config.getSerializerConfig
        .asInstanceOf[SerializerConfigImpl]
        .registerTypeWithKryoSerializer(
          classOf[GenericRecordWithSchemaId],
          classOf[GenericRecordWithSchemaIdSerializer]
        )
    }
  )

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def defaultFlinkKryoRoundTripSerialization(): AnyRef = {
    defaultFlinkKryoSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def schemaIdBasedKryoRoundTripSerialization(): AnyRef = {
    schemaIdBasedKryoSetup.roundTripSerialization()
  }

}
