package pl.touk.nussknacker.engine.benchmarks.nustruct

import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer

import java.util.concurrent.TimeUnit
import scala.io.Source

@State(Scope.Benchmark)
class NuStructBenchmark {

  val schema       = JsonSchemaBuilder.parseSchema(Source.fromResource("pgw.jsc").getLines().mkString)
  val deserializer = new CirceJsonDeserializer(schema)
  val json         = Source.fromResource("pgw_record.json").getLines().mkString

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def deserializeWithoutNuStruct(): AnyRef =
    deserializer.deserializeWithoutNuStruct(json)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def deserialize(): AnyRef =
    deserializer.deserialize(json)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def deserializeWithLazyMap(): AnyRef =
    deserializer.deserializeWithLazyMap(json)

}
