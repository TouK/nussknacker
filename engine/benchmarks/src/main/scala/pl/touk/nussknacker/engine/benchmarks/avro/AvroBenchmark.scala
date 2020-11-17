package pl.touk.nussknacker.engine.benchmarks.avro

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.apache.flink.formats.avro.typeutils.{LogicalTypesGenericRecordAvroTypeInfo, LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.CacheConfig

class AvroBenchmarkSetup[T <: GenericRecord](prepareConfig: ExecutionConfig => Unit, typeInfo: TypeInformation[T], record: T) {

  private val config = {
    val c = new ExecutionConfig
    prepareConfig(c)
    c
  }

  private val data = new ByteArrayOutputStream(10* 1024)

  private val serializer = typeInfo.createSerializer(config)

  def roundTripSerialization(): T = {
    data.reset()
    serializer.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
  }

}

@State(Scope.Thread)
class AvroBenchmark {

  private val avroKryoTypeInfo = TypeInformation.of(classOf[GenericData.Record])

  private val avroFlinkTypeInfo = new LogicalTypesGenericRecordAvroTypeInfo(AvroSamples.sampleSchema)

  private val avroFlinkWithSchemaIdTypeInfo = new LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo(AvroSamples.sampleSchema, AvroSamples.sampleSchemaId)

  private[avro] val defaultFlinkKryoSetup = new AvroBenchmarkSetup(
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config), avroKryoTypeInfo, AvroSamples.sampleRecord)

  private[avro] val defaultFlinkAvroSetup = new AvroBenchmarkSetup(_ => {}, avroFlinkTypeInfo, AvroSamples.sampleRecord)

  private[avro] val flinkAvroWithSchemaIdSetup = new AvroBenchmarkSetup(_ => {}, avroFlinkWithSchemaIdTypeInfo, AvroSamples.sampleRecordWithSchemaId)

  private[avro] val schemaIdBasedKryoSetup = new AvroBenchmarkSetup(
    config => {
      val schemaRegistryMockClient = new MockSchemaRegistryClient
      val parsedSchema = ConfluentUtils.convertToAvroSchema(AvroSamples.sampleSchema, Some(1))
      schemaRegistryMockClient.register("foo-value", parsedSchema, 1, AvroSamples.sampleSchemaId)
      val factory: CachedConfluentSchemaRegistryClientFactory =
        new CachedConfluentSchemaRegistryClientFactory(CacheConfig.defaultMaximumSize, None, None, None) {
          override protected def confluentClient(kafkaConfig: KafkaConfig): SchemaRegistryClient =
            schemaRegistryMockClient
        }
      val serializer = new SchemaIdBasedAvroGenericRecordSerializer(factory, KafkaConfig("fooKafkaAddress", None, None))
      config.getRegisteredTypesWithKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
      config.getDefaultKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
    }, avroKryoTypeInfo, AvroSamples.sampleRecordWithSchemaId)


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
