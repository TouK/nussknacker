package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesGenericRecordAvroTypeInfo, LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializer
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryClientKafkaConfig}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class AvroBenchmark {

  private val avroKryoTypeInfo = TypeInformation.of(classOf[GenericData.Record])

  private val avroFlinkTypeInfo = new LogicalTypesGenericRecordAvroTypeInfo(AvroSamples.sampleSchema)

  private val avroFlinkWithSchemaIdTypeInfo = new LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo(AvroSamples.sampleSchema, AvroSamples.sampleSchemaId)

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(avroKryoTypeInfo, AvroSamples.sampleRecord,
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config))

  private[avro] val defaultFlinkAvroSetup = new SerializationBenchmarkSetup(avroFlinkTypeInfo, AvroSamples.sampleRecord)

  private[avro] val flinkAvroWithSchemaIdSetup = new SerializationBenchmarkSetup(avroFlinkWithSchemaIdTypeInfo, AvroSamples.sampleRecordWithSchemaId)

  private[avro] val schemaIdBasedKryoSetup = new SerializationBenchmarkSetup(avroKryoTypeInfo, AvroSamples.sampleRecordWithSchemaId,
    config => {
      val schemaRegistryMockClient = new MockSchemaRegistryClient
      val parsedSchema = ConfluentUtils.convertToAvroSchema(AvroSamples.sampleSchema, Some(1))
      schemaRegistryMockClient.register("foo-value", parsedSchema, 1, AvroSamples.sampleSchemaId)
      val factory: CachedConfluentSchemaRegistryClientFactory =
        new CachedConfluentSchemaRegistryClientFactory {
          override protected def confluentClient(kafkaConfig: SchemaRegistryClientKafkaConfig): SchemaRegistryClient =
            schemaRegistryMockClient
        }
      val serializer = SchemaIdBasedAvroGenericRecordSerializer(factory, KafkaConfig("fooKafkaAddress", None, None))
      config.getRegisteredTypesWithKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
      config.getDefaultKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
    })


  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def defaultFlinkKryoRoundTripSerialization(): AnyRef = {
    defaultFlinkKryoSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def defaultFlinkAvroRoundTripSerialization(): AnyRef = {
    defaultFlinkAvroSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def schemaIdBasedKryoRoundTripSerialization(): AnyRef = {
    schemaIdBasedKryoSetup.roundTripSerialization()
  }

}
