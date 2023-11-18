package pl.touk.nussknacker.engine.benchmarks.nustruct

import org.everit.json.schema.Schema
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonTypedMap
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.util
import java.util.concurrent.TimeUnit
import scala.io.Source

@State(Scope.Benchmark)
class NuStructBenchmark {

  private val schema: Schema = JsonSchemaBuilder.parseSchema(Source.fromResource("pgw.schema.json").getLines().mkString)
  private val deserializer   = new CirceJsonDeserializer(schema)
  private val json: String   = Source.fromResource("pgw.record.json").getLines().mkString

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def deserialize(): AnyRef =
    deserializer.deserialize(json)

}
