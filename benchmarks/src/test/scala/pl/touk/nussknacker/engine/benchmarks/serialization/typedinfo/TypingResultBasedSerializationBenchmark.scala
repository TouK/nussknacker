package pl.touk.nussknacker.engine.benchmarks.serialization.typedinfo

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

/*
  Results for sample run are as follows:

  genericSerialization                      avgt    5  5.956 ± 0.175  us/op   => 253B
  typingResultSerialization                 avgt    5  0.978 ± 0.038  us/op   => 54B

  genericTypedMapSerialization              avgt    5  2.025 ± 0.036  us/op   => 60B
  typingResultTypedMapSerialization         avgt    5  0.438 ± 0.010  us/op   => 33B

  genericScalaMapSerialization              avgt    5  2.688 ± 0.075  us/op   => 68B
  typingResultScalaMapSerialization         avgt    5  0.579 ± 0.008  us/op   => 33B

  Using typing results should give 4-5 times better performance for round-trip serialization
  It also gives significant (2-5 times) reduction in serialized data size

 */
@State(Scope.Thread)
class TypingResultBasedSerializationBenchmark {

  private val detection = new TypingResultAwareTypeInformationDetection

  // we use TypedMap here to have TypedObjectTypingResult in Typed.fromInstance
  private val mapToSerialize = TypedMap(
    Map("field1" -> "strValue", "field2" -> 333L, "field3" -> 555, "field4" -> 555.3)
  )

  private val mapToSerializeType = Typed.fromInstance(mapToSerialize).asInstanceOf[TypedObjectTypingResult]

  private val contextToSerialize =
    Context("id").copy(variables = Map("var1" -> "11", "var2" -> 11L, "map" -> mapToSerialize))

  private val genericContextSetup =
    new SerializationBenchmarkSetup(TypeInformation.of(classOf[Context]), contextToSerialize)

  private val typingResultContextSetup = new SerializationBenchmarkSetup(
    detection.forContext(
      ValidationContext(Map("var1" -> Typed[String], "var2" -> Typed[Long], "map" -> mapToSerializeType))
    ),
    contextToSerialize
  )

  private val genericTypedMapSetup =
    new SerializationBenchmarkSetup(TypeInformation.of(classOf[TypedMap]), mapToSerialize)

  private val typingResultTypedMapSetup =
    new SerializationBenchmarkSetup(
      detection.forType(mapToSerializeType),
      mapToSerialize
    )

  private val genericScalaMapSetup =
    new SerializationBenchmarkSetup(TypeInformation.of(classOf[Map[String, Any]]), mapToSerialize.asScala.toMap)

  private val typingResultScalaMapSetup = new SerializationBenchmarkSetup(
    detection.forType(mapToSerializeType.copy(runtimeObjType = Typed.typedClass[Map[String, Any]])),
    mapToSerialize.asScala.toMap
  )

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def genericSerialization(): AnyRef = {
    genericContextSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def typingResultSerialization(): AnyRef = {
    typingResultContextSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def genericTypedMapSerialization(): AnyRef = {
    genericTypedMapSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def typingResultTypedMapSerialization(): AnyRef = {
    typingResultTypedMapSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def genericScalaMapSerialization(): AnyRef = {
    genericScalaMapSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def typingResultScalaMapSerialization(): AnyRef = {
    typingResultScalaMapSetup.roundTripSerialization()
  }

}
