package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.common.typeutils.base.{IntSerializer, LongSerializer, StringSerializer}
import org.apache.flink.api.java.typeutils.runtime.PojoSerializer
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.scalatest.Inside.inside
import org.scalatest.Inspectors.forAll
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.typeinformation.{FlinkTypeInfoRegistrar, TypeInformationDetection}
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject._

import java.nio.charset.{Charset, StandardCharsets}
import java.time._
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

class TypingResultAwareTypeInformationDetectionSpec
    extends AnyFunSuite
    with Matchers
    with FlinkTypeInformationSerializationMixin
    with OptionValues {

  private val detection = new TypingResultAwareTypeInformationDetection

  FlinkTypeInfoRegistrar.ensureTypeInfosAreRegistered()

  test("test map serialization") {
    val map = Map("intF" -> 11, "strF" -> "sdfasf", "longF" -> 111L, "fixedLong" -> 12L, "taggedString" -> "1")
    val typingResult = Typed.record(
      Map(
        "intF"         -> Typed[Int],
        "strF"         -> Typed[String],
        "longF"        -> Typed[Long],
        "fixedLong"    -> Typed.fromInstance(12L),
        "taggedString" -> Typed.tagged(Typed.typedClass[String], "someTag")
      ),
      Typed.typedClass[Map[String, Any]]
    )

    val typeInfo: TypeInformation[Map[String, Any]] = detection.forType(typingResult)

    serializeRoundTrip(map, typeInfo)()
    serializeRoundTrip(map - "longF", typeInfo)(map + ("longF" -> null))
    serializeRoundTrip(map + ("unknown" -> "???"), typeInfo)(map)

    assertMapSerializers(
      typeInfo.createSerializer(executionConfigWithoutKryo.getSerializerConfig),
      ("fixedLong", new LongSerializer),
      ("intF", new IntSerializer),
      ("longF", new LongSerializer),
      ("strF", new StringSerializer),
      ("taggedString", new StringSerializer)
    )
  }

  test("map serialization fallbacks to Kryo when available") {

    val map          = Map("obj" -> SomeTestClass("name"))
    val typingResult = Typed.record(Map("obj" -> Typed[SomeTestClass]), Typed.typedClass[Map[String, Any]])

    val typeInfo: TypeInformation[Map[String, Any]] = detection.forType(typingResult)

    an[UnsupportedOperationException] shouldBe thrownBy(serializeRoundTrip(map, typeInfo)())
    serializeRoundTrip(map, typeInfo, executionConfigWithKryo)()

    assertMapSerializers(
      typeInfo.createSerializer(executionConfigWithKryo.getSerializerConfig),
      ("obj", new KryoSerializer(classOf[SomeTestClass], executionConfigWithKryo.getSerializerConfig))
    )
  }

  test("test context serialization") {
    val ctx = Context.dummy.withVariables(
      Map(
        "one"            -> 11,
        "two"            -> "ala",
        "three"          -> Map("key" -> "value"),
        "arrayOfStrings" -> Array("foo", "bar", "baz"),
        "arrayOfInts"    -> Array[Integer](1, 2, 3),
      )
    )
    val vCtx = ValidationContext(
      Map(
        "one"            -> Typed[Int],
        "two"            -> Typed[String],
        "three"          -> Typed.record(Map("key" -> Typed[String]), Typed.typedClass[Map[String, Any]]),
        "arrayOfStrings" -> Typed.fromDetailedType[Array[String]],
        "arrayOfInts"    -> Typed.fromDetailedType[Array[Int]],
      )
    )

    val typeInfo          = detection.forContext(vCtx)
    val ctxAfterRoundTrip = getSerializeRoundTrip(ctx, typeInfo)
    checkContextAreSame(ctxAfterRoundTrip, ctx)

    val valueTypeInfo                  = detection.forValueWithContext[String](vCtx, Typed[String])
    val givenValue                     = "qwerty"
    val valueWithContextAfterRoundTrip = getSerializeRoundTrip(ValueWithContext[String](givenValue, ctx), valueTypeInfo)
    valueWithContextAfterRoundTrip.value shouldEqual givenValue
    checkContextAreSame(valueWithContextAfterRoundTrip.context, ctx)

    assertSerializersInContext(
      typeInfo.createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    )
  }

  test("serialization for logical types that can be represented as string") {
    val ctx = Context.dummy.withVariables(
      Map(
        "instant"        -> Instant.ofEpochMilli(123L),
        "offsetDateTime" -> OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        "zonedDateTime"  -> ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        "localDateTime"  -> LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0),
        "localDate"      -> LocalDate.of(2025, 1, 1),
        "localTime"      -> LocalTime.of(12, 1),
        "period"         -> Period.ofDays(30),
        "duration"       -> Duration.ofHours(12),
        "zoneOffset"     -> ZoneOffset.of("+01:00"),
        "zoneId"         -> ZoneId.of("Europe/Warsaw"),
        "locale"         -> Locale.ENGLISH,
        "charset"        -> StandardCharsets.UTF_8,
        "currency"       -> Currency.getInstance("USD"),
        "uuid"           -> UUID.fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf"),
      )
    )

    val vCtx = ValidationContext.empty.copy(localVariables =
      Map(
        "instant"        -> Typed[Instant],
        "offsetDateTime" -> Typed[OffsetDateTime],
        "zonedDateTime"  -> Typed[ZonedDateTime],
        "localDateTime"  -> Typed[LocalDateTime],
        "localDate"      -> Typed[LocalDate],
        "localTime"      -> Typed[LocalTime],
        "period"         -> Typed[Period],
        "duration"       -> Typed[Duration],
        "zoneOffset"     -> Typed[ZoneOffset],
        "zoneId"         -> Typed[ZoneId],
        "locale"         -> Typed[Locale],
        "charset"        -> Typed[Charset],
        "currency"       -> Typed[Currency],
        "uuid"           -> Typed[UUID],
      )
    )

    val typeInfo          = detection.forContext(vCtx)
    val ctxAfterRoundTrip = getSerializeRoundTrip(ctx, typeInfo)
    checkContextAreSame(ctxAfterRoundTrip, ctx)
  }

  // This is not exactly intended behaviour - the test is here to show problems with static type definitions
  test("number promotion behaviour") {
    val vCtx = ValidationContext(Map("longField" -> Typed[Long])) // we declare Long variable

    val ctx = Context.dummy.withVariables(
      Map("longField" -> 11)
    ) // but we put Int in runtime (which e.g. in spel wouldn't be a problem...)!

    val typeInfo = detection.forContext(vCtx)
    intercept[ClassCastException](serializeRoundTrip(ctx, typeInfo)())

    assertSerializersInContext(
      typeInfo.createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    )
  }

  test("Test serialization compatibility") {
    val typingResult =
      Typed.record(Map("intF" -> Typed[Int], "strF" -> Typed[String]), Typed.typedClass[Map[String, Any]])
    val compatibleTypingResult = Typed.record(
      Map("intF" -> Typed[Int], "strF" -> Typed[String], "longF" -> Typed[Long]),
      Typed.typedClass[Map[String, Any]]
    )
    val incompatibleTypingResult =
      Typed.record(Map("intF" -> Typed[Int], "strF" -> Typed[Long]), Typed.typedClass[Map[String, Any]])

    val serializer =
      detection.forType(typingResult).createSerializer(executionConfigWithoutKryo.getSerializerConfig)

    val compatibleSerializer =
      detection
        .forType(compatibleTypingResult)
        .createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    val incompatibleSerializer =
      detection
        .forType(incompatibleTypingResult)
        .createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    val serializerSnapshot = serializer.snapshotConfiguration()

    serializerSnapshot
      .resolveSchemaCompatibility(serializerSnapshot)
      .isCompatibleAsIs shouldBe true
    compatibleSerializer
      .snapshotConfiguration()
      .resolveSchemaCompatibility(serializerSnapshot)
      .isCompatibleAfterMigration shouldBe true
    incompatibleSerializer
      .snapshotConfiguration()
      .resolveSchemaCompatibility(serializerSnapshot)
      .isIncompatible shouldBe true
  }

  test("serialization compatibility with custom flag config") {
    val typingResult = Typed.record(Map("intF" -> Typed[Int], "strF" -> Typed[String]))
    val addField     = Typed.record(Map("intF" -> Typed[Int], "strF" -> Typed[String], "longF" -> Typed[Long]))
    val removeField  = Typed.record(Map("intF" -> Typed[Int]))

    serializeRoundTrip(
      Map[String, AnyRef]("intF" -> (5: java.lang.Integer), "strF" -> "").asJava,
      detection.forType(typingResult)
    )()

    val oldSerializer =
      detection.forType(typingResult).createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    val addFieldSerializer =
      detection.forType(addField).createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    val removeFieldSerializer =
      detection.forType(removeField).createSerializer(executionConfigWithoutKryo.getSerializerConfig)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()

    oldSerializerSnapshot
      .resolveSchemaCompatibility(oldSerializerSnapshot)
      .isCompatibleAsIs shouldBe true
    addFieldSerializer
      .snapshotConfiguration()
      .resolveSchemaCompatibility(oldSerializerSnapshot)
      .isCompatibleAfterMigration shouldBe true
    removeFieldSerializer
      .snapshotConfiguration()
      .resolveSchemaCompatibility(oldSerializerSnapshot)
      .isCompatibleAfterMigration shouldBe true
  }

  test("return type info for LocalDate, LocalTime and LocalDateTime even if type info registration is disabled") {
    withFlinkTypeInfoRegistrationDisabled {
      TypeInformationDetection.instance.forClass[LocalDate] shouldBe Types.LOCAL_DATE
      TypeInformationDetection.instance.forClass[LocalTime] shouldBe Types.LOCAL_TIME
      TypeInformationDetection.instance.forClass[LocalDateTime] shouldBe Types.LOCAL_DATE_TIME
    }
  }

  private def withFlinkTypeInfoRegistrationDisabled[T](f: => T): T = {
    val stateBeforeChange = FlinkTypeInfoRegistrar.isFlinkTypeInfoRegistrationEnabled
    FlinkTypeInfoRegistrar.disableFlinkTypeInfoRegistration()
    try {
      f
    } finally {
      if (stateBeforeChange) {
        FlinkTypeInfoRegistrar.enableFlinkTypeInfoRegistration()
      }
    }
  }

  // We have to compare it this way because context can contains arrays
  private def checkContextAreSame(givenContext: Context, expectedContext: Context): Unit = {
    givenContext.id shouldEqual expectedContext.id
    givenContext.variables.keys should contain theSameElementsAs expectedContext.variables.keys
    forAll(givenContext.variables) { case (variableName, variableValue) =>
      expectedContext.variables.get(variableName).value shouldEqual variableValue
    }
    inside((givenContext.parentContext, expectedContext.parentContext)) {
      case (None, None) =>
      case (Some(givenParent: Context), Some(expectedParent: Context)) =>
        checkContextAreSame(givenParent, expectedParent)
    }
  }

  private def assertSerializersInContext(
      serializer: TypeSerializer[_],
  ): Unit = {
    serializer shouldBe a[PojoSerializer[_]]
  }

  private def assertMapSerializers(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_])*) = {
    inside(serializer.asInstanceOf[TypeSerializer[Map[String, _ <: AnyRef]]]) { case TypedScalaMapSerializer(array) =>
      array.toList shouldBe nested.toList
    }
  }

}

case class SomeTestClass(name: String)

//Sample implementation of TypeObjectTypingResult
object testTypedObject {

  case class CustomTypedObject(map: java.util.Map[String, AnyRef]) extends java.util.HashMap[String, AnyRef](map)

}
