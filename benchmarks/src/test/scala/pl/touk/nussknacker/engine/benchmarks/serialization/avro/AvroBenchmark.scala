package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import com.typesafe.config.ConfigFactory
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.{
  AvroSerializersRegistrar,
  GenericRecordWithSchemaIdTypeInfo
}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class AvroBenchmark {

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(
    TypeInformation.of(classOf[GenericData.Record]),
    AvroSamples.sampleRecord,
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config)
  )

  private[avro] val schemaIdBasedKryoSetup = new SerializationBenchmarkSetup(
    new GenericRecordWithSchemaIdTypeInfo,
    AvroSamples.sampleRecordWithSchemaId,
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
