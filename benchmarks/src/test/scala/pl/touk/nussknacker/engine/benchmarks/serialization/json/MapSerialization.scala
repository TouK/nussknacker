package pl.touk.nussknacker.engine.benchmarks.serialization.json

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.everit.json.schema.Schema
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.json.serde.CirceJsonDeserializer
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonTypedMap
import pl.touk.nussknacker.engine.process.util.Serializers

import java.util.concurrent.TimeUnit
import scala.io.Source

@State(Scope.Thread)
class MapSerialization {

  val schema: Schema        = JsonSchemaBuilder.parseSchema(Source.fromResource("pgw.schema.json").getLines().mkString)
  val deserializer          = new CirceJsonDeserializer(schema)
  val json: String          = Source.fromResource("pgw.record.json").getLines().mkString
  val lazyMap: JsonTypedMap = deserializer.deserialize(json).asInstanceOf[JsonTypedMap]
  val typeMap: java.util.Map[String, Any] = lazyMap.materialize

  val lazyMapSetup = new SerializationBenchmarkSetup(
    TypeInformation.of(classOf[JsonTypedMap]),
    lazyMap,
    config => Serializers.CaseClassSerializer.registerIn(config)
  )

  val TypedMapSetup = new SerializationBenchmarkSetup(
    TypeInformation.of(classOf[TypedMap]),
    typeMap
  )

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def lazyMapSerialization() =
    lazyMapSetup.roundTripSerialization()

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def typeMapSerialization() =
    TypedMapSetup.roundTripSerialization()

}
