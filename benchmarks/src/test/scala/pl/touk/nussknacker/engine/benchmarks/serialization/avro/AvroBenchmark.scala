package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.process.util.Serializers

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class AvroBenchmark {

  private val avroKryoTypeInfo = TypeInformation.of(classOf[GenericData.Record])

  private[avro] val defaultFlinkKryoSetup = new SerializationBenchmarkSetup(
    avroKryoTypeInfo,
    AvroSamples.sampleRecord,
    Serializers.registerSerializers(getClass.getClassLoader, _)
  )

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def defaultFlinkKryoRoundTripSerialization(): AnyRef = {
    defaultFlinkKryoSetup.roundTripSerialization()
  }

}
