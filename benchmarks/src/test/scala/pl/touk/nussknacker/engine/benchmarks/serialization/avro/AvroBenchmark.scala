package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import com.typesafe.config.ConfigFactory
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.schemedkafka.kryo.AvroSerializersRegistrar

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class AvroBenchmark {

  private val avroKryoTypeInfo = TypeInformation.of(classOf[GenericData.Record])

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(
    avroKryoTypeInfo,
    AvroSamples.sampleRecord,
    config => new AvroSerializersRegistrar().register(ConfigFactory.empty(), config)
  )

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def defaultFlinkKryoRoundTripSerialization(): AnyRef = {
    defaultFlinkKryoSetup.roundTripSerialization()
  }

}
