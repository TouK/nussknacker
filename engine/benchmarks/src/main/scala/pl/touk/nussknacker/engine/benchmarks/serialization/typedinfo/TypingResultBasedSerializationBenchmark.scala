package pl.touk.nussknacker.engine.benchmarks.serialization.typedinfo

import java.util.concurrent.TimeUnit

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, State}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import scala.collection.JavaConverters._

@State(Scope.Thread)
class TypingResultBasedSerializationBenchmark {

  private val contextToSerialize = Context("id").copy(variables = Map(
    "var1" -> "11",
    "var2" -> 11L,
    "map" -> new TypedMap(Map("field1" -> "strValue", "field2" -> 333L).asJava)))

  private val determiner = new TypingResultAwareTypeInformationDetection()

  private val genericSetup = new SerializationBenchmarkSetup(TypeInformation.of(classOf[Context]), contextToSerialize)
  
  private val typingResultSetup = new SerializationBenchmarkSetup(
    determiner.forContext(ValidationContext(Map(
      "var1" -> Typed[String],
      "var2" -> Typed[Long],
      "map" -> TypedObjectTypingResult(Map("field1" -> Typed[String], "field2" -> Typed[Long]), Typed.typedClass[TypedMap])
    ))), contextToSerialize)


  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def genericSerialization(): AnyRef = {
    genericSetup.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def typingResultSerialization(): AnyRef = {
    typingResultSetup.roundTripSerialization()
  }


}

