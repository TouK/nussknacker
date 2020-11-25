package pl.touk.nussknacker.engine.process.typeinformation

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializerSchemaCompatibility
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

class TypingResultAwareTypeInformationDetectionSpec extends FunSuite with Matchers {

  private val informationDetection = new TypingResultAwareTypeInformationDetection()

  private val executionConfigWithoutKryo = new ExecutionConfig {
    disableGenericTypes()
  }

  private val executionConfigWithKryo = new ExecutionConfig {
    enableGenericTypes()
  }

  test("test map serialization") {
    val map = Map("intF" -> 11, "strF" -> "sdfasf", "longF" -> 111L)
    val typingResult = TypedObjectTypingResult(Map("intF" -> Typed[Int], "strF" -> Typed[String],
      "longF" -> Typed[Long]), Typed.typedClass[Map[String, Any]])

    val typeInfo = informationDetection.forType(typingResult)

    serializeRoundTrip(map, typeInfo)()
    serializeRoundTrip(map - "longF", typeInfo)(map + ("longF" -> null))
    serializeRoundTrip(map + ("unknown" -> "???"), typeInfo)(map)
  }

  test("map serialization fallbacks to Kryo when available") {

    val map = Map("obj" -> SomeTestClass("name"))
    val typingResult = TypedObjectTypingResult(Map("obj" -> Typed[SomeTestClass]), Typed.typedClass[Map[String, Any]])

    val typeInfo = informationDetection.forType(typingResult)

    an [UnsupportedOperationException] shouldBe thrownBy (serializeRoundTrip(map, typeInfo)())
    serializeRoundTrip(map, typeInfo, executionConfigWithKryo)()

  }

  test("test context serialization") {
    val ctx = Context("11").copy(variables = Map("one" -> 11, "two" -> "ala", "three" -> Map("key" -> "value")))
    val vCtx = ValidationContext(Map("one" -> Typed[Int], "two" -> Typed[String], "three" -> TypedObjectTypingResult(Map("key" -> Typed[String]),
      Typed.typedClass[Map[String, Any]])))

    val typeInfo = informationDetection.forContext(vCtx)
    serializeRoundTrip(ctx, typeInfo)()

    val valueTypeInfo = informationDetection.forValueWithContext[String](vCtx, Typed[String])
    serializeRoundTrip(ValueWithContext[String]("qwerty", ctx), valueTypeInfo)()
  }

  test("Test serialization compatibility") {
    val typingResult = TypedObjectTypingResult(Map("intF" -> Typed[Int], "strF" -> Typed[String]),
      Typed.typedClass[Map[String, Any]])
    val compatibleTypingResult = TypedObjectTypingResult(Map("intF" -> Typed[Int], "strF" -> Typed[String],
          "longF" -> Typed[Long]), Typed.typedClass[Map[String, Any]])
    val incompatibleTypingResult = TypedObjectTypingResult(Map("intF" -> Typed[Int], "strF" -> Typed[Long]),
      Typed.typedClass[Map[String, Any]])

    val oldSerializer = informationDetection.forType(typingResult).createSerializer(executionConfigWithoutKryo)
    val compatibleSerializer = informationDetection.forType(compatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val incompatibleSerializer = informationDetection.forType(incompatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()


    oldSerializerSnapshot.resolveSchemaCompatibility(oldSerializer).isCompatibleAsIs shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(compatibleSerializer).isCompatibleAfterMigration shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(incompatibleSerializer).isIncompatible shouldBe true
  }

  private def serializeRoundTrip[T](record: T, typeInfo: TypeInformation[T], executionConfig: ExecutionConfig
      = executionConfigWithoutKryo)(expected:T = record) = {
    val data = new ByteArrayOutputStream(10 * 1024)
    val serializer = typeInfo.createSerializer(executionConfig)
    serializer.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    val out = serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
    out shouldBe expected
  }

}

case class SomeTestClass(name: String)
