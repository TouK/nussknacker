package pl.touk.nussknacker.engine.process.typeinformation

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.base.array.{IntPrimitiveArraySerializer, StringArraySerializer}
import org.apache.flink.api.common.typeutils.base.{
  GenericArraySerializer,
  IntSerializer,
  LongSerializer,
  StringSerializer
}
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.scalatest.Inside.inside
import org.scalatest.Inspectors.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, OptionValues}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.ScalaCaseClassSerializer
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject._
import pl.touk.nussknacker.engine.process.typeinformation.testTypedObject.{
  CustomObjectTypeInformation,
  CustomTypedObject
}
import pl.touk.nussknacker.engine.util.Implicits._

import java.util.Collections
import scala.jdk.CollectionConverters._

class TypingResultAwareTypeInformationDetectionSpec
    extends AnyFunSuite
    with Matchers
    with FlinkTypeInformationSerializationMixin
    with OptionValues {

  private val informationDetection =
    new TypingResultAwareTypeInformationDetection((originalDetection: TypeInformationDetection) => {
      case e: TypedObjectTypingResult if e.objType == Typed.typedClass[CustomTypedObject] =>
        CustomObjectTypeInformation(e.fields.mapValuesNow(originalDetection.forType))
    })

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

    val typeInfo: TypeInformation[Map[String, Any]] = informationDetection.forType(typingResult)

    serializeRoundTrip(map, typeInfo)()
    serializeRoundTrip(map - "longF", typeInfo)(map + ("longF" -> null))
    serializeRoundTrip(map + ("unknown" -> "???"), typeInfo)(map)

    assertMapSerializers(
      typeInfo.createSerializer(executionConfigWithoutKryo),
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

    val typeInfo: TypeInformation[Map[String, Any]] = informationDetection.forType(typingResult)

    an[UnsupportedOperationException] shouldBe thrownBy(serializeRoundTrip(map, typeInfo)())
    serializeRoundTrip(map, typeInfo, executionConfigWithKryo)()

    assertMapSerializers(
      typeInfo.createSerializer(executionConfigWithKryo),
      ("obj", new KryoSerializer(classOf[SomeTestClass], executionConfigWithKryo))
    )
  }

  test("test context serialization") {
    val ctx = Context("11").copy(variables =
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

    val typeInfo          = informationDetection.forContext(vCtx)
    val ctxAfterRoundTrip = getSerializeRoundTrip(ctx, typeInfo)
    checkContextAreSame(ctxAfterRoundTrip, ctx)

    val valueTypeInfo                  = informationDetection.forValueWithContext[String](vCtx, Typed[String])
    val givenValue                     = "qwerty"
    val valueWithContextAfterRoundTrip = getSerializeRoundTrip(ValueWithContext[String](givenValue, ctx), valueTypeInfo)
    valueWithContextAfterRoundTrip.value shouldEqual givenValue
    checkContextAreSame(valueWithContextAfterRoundTrip.context, ctx)

    assertSerializersInContext(
      typeInfo.createSerializer(executionConfigWithoutKryo),
      ("arrayOfInts", _ shouldBe new GenericArraySerializer(classOf[Integer], new IntSerializer)),
      ("arrayOfStrings", _ shouldBe new StringArraySerializer),
      ("one", _ shouldBe new IntSerializer),
      ("three", assertMapSerializers(_, ("key", new StringSerializer))),
      ("two", _ shouldBe new StringSerializer)
    )
  }

  // This is not exactly intended behaviour - the test is here to show problems with static type definitions
  test("number promotion behaviour") {
    val vCtx = ValidationContext(Map("longField" -> Typed[Long])) // we declare Long variable

    val ctx = Context("11").copy(variables =
      Map("longField" -> 11)
    ) // but we put Int in runtime (which e.g. in spel wouldn't be a problem...)!

    val typeInfo = informationDetection.forContext(vCtx)
    intercept[ClassCastException](serializeRoundTrip(ctx, typeInfo)())

    assertSerializersInContext(
      typeInfo.createSerializer(executionConfigWithoutKryo),
      ("longField", _ shouldBe new LongSerializer)
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

    val oldSerializer = informationDetection.forType(typingResult).createSerializer(executionConfigWithoutKryo)

    val compatibleSerializer =
      informationDetection.forType(compatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val incompatibleSerializer =
      informationDetection.forType(incompatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()

    oldSerializerSnapshot.resolveSchemaCompatibility(oldSerializer).isCompatibleAsIs shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(compatibleSerializer).isCompatibleAfterMigration shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(incompatibleSerializer).isIncompatible shouldBe true
  }

  test("serialization compatibility with reconfigured serializer") {

    val map          = Map("obj" -> SomeTestClass("name"))
    val typingResult = Typed.record(Map("obj" -> Typed[SomeTestClass]), Typed.typedClass[Map[String, Any]])

    val oldSerializer =
      informationDetection.forType[Map[String, Any]](typingResult).createSerializer(executionConfigWithKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()

    // we prepare ExecutionConfig with different Kryo config, it causes need to reconfigure kryo serializer, used for SomeTestClass
    val newExecutionConfig = new ExecutionConfig {
      registerTypeWithKryoSerializer(classOf[CustomTypedObject], classOf[DummySerializer])
    }
    val newSerializer =
      informationDetection.forType[Map[String, Any]](typingResult).createSerializer(newExecutionConfig)
    val compatibility = oldSerializerSnapshot.resolveSchemaCompatibility(newSerializer)

    compatibility.isCompatibleWithReconfiguredSerializer shouldBe true
    val reconfigured = compatibility.getReconfiguredSerializer
    serializeRoundTripWithSerializers(map, oldSerializer, reconfigured)()
  }

  test("serialization compatibility with custom flag config") {
    val typingResult =
      Typed.record(Map("intF" -> Typed[Int], "strF" -> Typed[String]), Typed.typedClass[CustomTypedObject])
    val addField = Typed.record(
      Map("intF" -> Typed[Int], "strF" -> Typed[String], "longF" -> Typed[Long]),
      Typed.typedClass[CustomTypedObject]
    )
    val removeField = Typed.record(Map("intF" -> Typed[Int]), Typed.typedClass[CustomTypedObject])

    serializeRoundTrip(
      CustomTypedObject(Map[String, AnyRef]("intF" -> (5: java.lang.Integer), "strF" -> "").asJava),
      informationDetection.forType(typingResult)
    )()

    val oldSerializer         = informationDetection.forType(typingResult).createSerializer(executionConfigWithoutKryo)
    val addFieldSerializer    = informationDetection.forType(addField).createSerializer(executionConfigWithoutKryo)
    val removeFieldSerializer = informationDetection.forType(removeField).createSerializer(executionConfigWithoutKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()

    oldSerializerSnapshot.resolveSchemaCompatibility(oldSerializer).isCompatibleAsIs shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(addFieldSerializer).isIncompatible shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(removeFieldSerializer).isIncompatible shouldBe true
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
      nested: (String, TypeSerializer[_] => Assertion)*
  ): Unit = {
    inside(serializer.asInstanceOf[TypeSerializer[Context]]) { case e: ScalaCaseClassSerializer[Context] @unchecked =>
      e.getFieldSerializers should have length 3
      assertNested(e.getFieldSerializers.apply(1), nested: _*)

    }
  }

  private def assertNested(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_] => Assertion)*): Unit = {
    inside(serializer.asInstanceOf[TypeSerializer[Map[String, _ <: AnyRef]]]) { case TypedScalaMapSerializer(array) =>
      array.zipAll(nested.toList, null, null).foreach { case ((name, serializer), (expectedName, expectedSerializer)) =>
        name shouldBe expectedName
        expectedSerializer(serializer)
      }
    }
  }

  private def assertMapSerializers(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_])*) = {
    inside(serializer.asInstanceOf[TypeSerializer[Map[String, _ <: AnyRef]]]) { case TypedScalaMapSerializer(array) =>
      array.toList shouldBe nested.toList
    }
  }

}

case class SomeTestClass(name: String)

class DummySerializer extends Serializer[CustomTypedObject] {
  override def write(kryo: Kryo, output: Output, `object`: CustomTypedObject): Unit = ???

  override def read(kryo: Kryo, input: Input, `type`: Class[CustomTypedObject]): CustomTypedObject = ???
}

//Sample implementation of TypeObjectTypingResult
object testTypedObject {

  case class CustomTypedObject(map: java.util.Map[String, AnyRef]) extends java.util.HashMap[String, AnyRef](map)

  case class CustomObjectTypeInformation(fields: Map[String, TypeInformation[_]])
      extends TypedObjectBasedTypeInformation[CustomTypedObject](fields) {
    override def createSerializer(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[CustomTypedObject] =
      CustomObjectTypeSerializer(serializers)
  }

  case class CustomObjectTypeSerializer(override val serializers: Array[(String, TypeSerializer[_])])
      extends TypedObjectBasedTypeSerializer[CustomTypedObject](serializers)
      with BaseJavaMapBasedSerializer[AnyRef, CustomTypedObject] {

    override def snapshotConfiguration(
        snapshots: Array[(String, TypeSerializerSnapshot[_])]
    ): TypeSerializerSnapshot[CustomTypedObject] = new CustomObjectSerializerSnapshot(snapshots)

    override def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[CustomTypedObject] =
      CustomObjectTypeSerializer(serializers)

    override def createInstance(): CustomTypedObject = CustomTypedObject(Collections.emptyMap())

  }

  class CustomObjectSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[CustomTypedObject] {

    def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
      this()
      this.serializersSnapshots = serializers
    }

    override protected def compatibilityRequiresSameKeys: Boolean = true

    override protected def restoreSerializer(
        restored: Array[(String, TypeSerializer[_])]
    ): TypeSerializer[CustomTypedObject] = CustomObjectTypeSerializer(restored)

  }

}
