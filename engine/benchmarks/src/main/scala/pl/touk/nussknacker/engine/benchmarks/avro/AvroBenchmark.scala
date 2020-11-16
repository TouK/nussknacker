package pl.touk.nussknacker.engine.benchmarks.avro

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.apache.flink.formats.avro.typeutils.LogicalTypesGenericRecordAvroTypeInfo
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.avro.kryo.AvroSerializersRegistrar

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

  private[avro] val defaultFlinkKryoSetup = new AvroBenchmarkSetup(
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config), avroKryoTypeInfo, AvroSamples.sampleRecord)

  private[avro] val defaultFlinkAvroSetup = new AvroBenchmarkSetup(_ => {}, avroFlinkTypeInfo, AvroSamples.sampleRecord)

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

}
