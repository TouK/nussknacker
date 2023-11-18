package pl.touk.nussknacker.engine.benchmarks.serialization.json

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
class JsonLazyMaptoJsonBenchmark {

  val schema: Schema        = JsonSchemaBuilder.parseSchema(Source.fromResource("pgw.schema.json").getLines().mkString)
  val deserializer          = new CirceJsonDeserializer(schema)
  val json: String          = Source.fromResource("pgw.record.json").getLines().mkString
  val lazyMap: JsonTypedMap = deserializer.deserialize(json).asInstanceOf[JsonTypedMap]
  val typeMap: util.HashMap[String, Any] = new java.util.HashMap(lazyMap)
  val jsonEncoder: BestEffortJsonEncoder = BestEffortJsonEncoder.defaultForTests

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def encodeLazyMapToJson(): AnyRef =
    jsonEncoder.encode(lazyMap)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def encodeTypedMapToJson(): AnyRef =
    jsonEncoder.encode(typeMap)

}
