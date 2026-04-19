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

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(
    TypeInformation.of(classOf[GenericData.Record]),
    AvroSamples.sampleRecord,
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config)
  )

  private[avro] val schemaIdBasedKryoSetup = new SerializationBenchmarkSetup(
    TypeInformation.of(classOf[GenericData.Record]),
    AvroSamples.sampleRecordWithSchemaId,
    config => {
      val schemaRegistryMockClient = new MockSchemaRegistryClient
      val parsedSchema             = ConfluentUtils.convertToAvroSchema(AvroSamples.sampleSchema, Some(1))
      schemaRegistryMockClient.register("foo-value", parsedSchema, 1, AvroSamples.sampleSchemaId.asInt)
      val factory                         = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
      val schemaRegistryClientKafkaConfig = SchemaRegistryClientKafkaConfig(Map("bootstrap.servers" -> "noKafka"))

      val serializer = new GenericRecordWithSchemaIdSerializer(
        factory,
        Map(AvroSamples.sampleSchemaRegistryId -> schemaRegistryClientKafkaConfig)
      )

      config.getSerializerConfig
        .asInstanceOf[SerializerConfigImpl]
        .registerTypeWithKryoSerializer(
          classOf[GenericRecordWithSchemaId],
          serializer
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
