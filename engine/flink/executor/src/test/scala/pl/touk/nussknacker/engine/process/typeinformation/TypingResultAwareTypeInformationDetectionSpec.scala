package pl.touk.nussknacker.engine.process.typeinformation

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}

import java.util.Collections
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.base.{IntSerializer, LongSerializer, StringSerializer}
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.api.scala.typeutils.ScalaCaseClassSerializer
import org.scalatest.Inside.inside
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed.fromInstance
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{BaseJavaMapBasedSerializer, TypedObjectBasedSerializerSnapshot, TypedObjectBasedTypeInformation, TypedObjectBasedTypeSerializer, TypedScalaMapSerializer}
import pl.touk.nussknacker.engine.process.typeinformation.testTypedObject.{CustomObjectTypeInformation, CustomTypedObject}
import pl.touk.nussknacker.engine.util.Implicits._

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

class TypingResultAwareTypeInformationDetectionSpec extends FunSuite with Matchers with FlinkTypeInformationSerializationMixin {

  private val informationDetection = new TypingResultAwareTypeInformationDetection(new TypingResultAwareTypeInformationCustomisation {
    override def customise(originalDetection: TypeInformationDetection): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
      case e: TypedObjectTypingResult if e.objType == Typed.typedClass[CustomTypedObject] =>
        CustomObjectTypeInformation(e.fields.mapValuesNow(originalDetection.forType))
    }
  })

  test("test map serialization") {
    val map = Map("intF" -> 11, "strF" -> "sdfasf", "longF" -> 111L)
    val typingResult = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[String],
      "longF" -> Typed[Long]), Typed.typedClass[Map[String, Any]])

    val typeInfo = informationDetection.forType(typingResult)

    serializeRoundTrip(map, typeInfo)()
    serializeRoundTrip(map - "longF", typeInfo)(map + ("longF" -> null))
    serializeRoundTrip(map + ("unknown" -> "???"), typeInfo)(map)

    assertMapSerializers(typeInfo.createSerializer(executionConfigWithoutKryo),
      ("intF", new IntSerializer),
      ("longF", new LongSerializer),
      ("strF", new StringSerializer)
    )
  }

  test("test Traversable serialization") {

    List[(Traversable[String], TypingResult)](
      (List("a", "b", "c", "d"), Typed.fromDetailedType[List[String]])
    ).foreach { case (traversable, typ) =>
      val typeInfo = informationDetection.forType(typ)
      serializeRoundTrip[AnyRef](traversable, typeInfo)(traversable)
    }
  }

  test("map serialization fallbacks to Kryo when available") {

    val map = Map("obj" -> SomeTestClass("name"))
    val typingResult = TypedObjectTypingResult(ListMap("obj" -> Typed[SomeTestClass]), Typed.typedClass[Map[String, Any]])

    val typeInfo = informationDetection.forType(typingResult)

    an [UnsupportedOperationException] shouldBe thrownBy (serializeRoundTrip(map, typeInfo)())
    serializeRoundTrip(map, typeInfo, executionConfigWithKryo)()

    assertMapSerializers(typeInfo.createSerializer(executionConfigWithKryo),  ("obj", new KryoSerializer(classOf[SomeTestClass], executionConfigWithKryo)))
  }

  test("test context serialization") {
    val ctx = Context("11").copy(variables = Map("one" -> 11, "two" -> "ala", "three" -> Map("key" -> "value")))
    val vCtx = ValidationContext(Map("one" -> Typed[Int], "two" -> Typed[String], "three" -> TypedObjectTypingResult(ListMap("key" -> Typed[String]),
      Typed.typedClass[Map[String, Any]])))

    val typeInfo = informationDetection.forContext(vCtx)
    serializeRoundTrip(ctx, typeInfo)()

    val valueTypeInfo = informationDetection.forValueWithContext[String](vCtx, Typed[String])
    serializeRoundTrip(ValueWithContext[String]("qwerty", ctx), valueTypeInfo)()

    assertSerializersInContext(typeInfo.createSerializer(executionConfigWithoutKryo),
      ("one", _ shouldBe new IntSerializer),
      ("three", assertMapSerializers(_, ("key", new StringSerializer))),
      ("two", _ shouldBe new StringSerializer)
    )
  }

  //This is not exactly intended behaviour - the test is here to show problems with static type definitions
  test("number promotion behaviour") {
    val vCtx = ValidationContext(Map("longField" -> Typed[Long])) //we declare Long variable

    val ctx = Context("11").copy(variables = Map("longField" -> 11)) //but we put Int in runtime (which e.g. in spel wouldn't be a problem...)!

    val typeInfo = informationDetection.forContext(vCtx)
    intercept[ClassCastException](serializeRoundTrip(ctx, typeInfo)())

    assertSerializersInContext(typeInfo.createSerializer(executionConfigWithoutKryo),
      ("longField", _ shouldBe new LongSerializer)
    )
  }

  test("Test serialization compatibility") {
    val typingResult = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[String]),
      Typed.typedClass[Map[String, Any]])
    val compatibleTypingResult = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[String],
          "longF" -> Typed[Long]), Typed.typedClass[Map[String, Any]])
    val incompatibleTypingResult = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[Long]),
      Typed.typedClass[Map[String, Any]])

    val oldSerializer = informationDetection.forType(typingResult).createSerializer(executionConfigWithoutKryo)

    val compatibleSerializer = informationDetection.forType(compatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val incompatibleSerializer = informationDetection.forType(incompatibleTypingResult).createSerializer(executionConfigWithoutKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()


    oldSerializerSnapshot.resolveSchemaCompatibility(oldSerializer).isCompatibleAsIs shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(compatibleSerializer).isCompatibleAfterMigration shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(incompatibleSerializer).isIncompatible shouldBe true
  }

  test("serialization compatibility with reconfigured serializer") {

    val map = Map("obj" -> SomeTestClass("name"))
    val typingResult = TypedObjectTypingResult(ListMap("obj" -> Typed[SomeTestClass]), Typed.typedClass[Map[String, Any]])

    val oldSerializer = informationDetection.forType(typingResult).createSerializer(executionConfigWithKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()

    //we prepare ExecutionConfig with different Kryo config, it causes need to reconfigure kryo serializer, used for SomeTestClass
    val newExecutionConfig = new ExecutionConfig {
      registerTypeWithKryoSerializer(classOf[CustomTypedObject], classOf[DummySerializer])
    }
    val newSerializer = informationDetection.forType(typingResult).createSerializer(newExecutionConfig)
    val compatibility = oldSerializerSnapshot.resolveSchemaCompatibility(newSerializer)

    compatibility.isCompatibleWithReconfiguredSerializer shouldBe true
    val reconfigured = compatibility.getReconfiguredSerializer
    serializeRoundTripWithSerializers(map, oldSerializer, reconfigured)()
  }

  test("serialization compatibility with custom flag config") {
    val typingResult = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[String]),
      Typed.typedClass[CustomTypedObject])
    val addField = TypedObjectTypingResult(ListMap("intF" -> Typed[Int], "strF" -> Typed[String],
      "longF" -> Typed[Long]), Typed.typedClass[CustomTypedObject])
    val removeField = TypedObjectTypingResult(ListMap("intF" -> Typed[Int]), Typed.typedClass[CustomTypedObject])

    serializeRoundTrip(CustomTypedObject(Map[String, AnyRef]("intF" -> (5: java.lang.Integer), "strF" -> "").asJava), informationDetection.forType(typingResult))()

    val oldSerializer = informationDetection.forType(typingResult).createSerializer(executionConfigWithoutKryo)
    val addFieldSerializer = informationDetection.forType(addField).createSerializer(executionConfigWithoutKryo)
    val removeFieldSerializer = informationDetection.forType(removeField).createSerializer(executionConfigWithoutKryo)
    val oldSerializerSnapshot = oldSerializer.snapshotConfiguration()


    oldSerializerSnapshot.resolveSchemaCompatibility(oldSerializer).isCompatibleAsIs shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(addFieldSerializer).isIncompatible shouldBe true
    oldSerializerSnapshot.resolveSchemaCompatibility(removeFieldSerializer).isIncompatible shouldBe true
  }

  private def assertSerializersInContext(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_] => Assertion)*): Unit = {
    inside(serializer.asInstanceOf[TypeSerializer[Context]])  {
      case e:ScalaCaseClassSerializer[Context]@unchecked =>
        e.getFieldSerializers should have length 3
        assertNested(e.getFieldSerializers.apply(1), nested: _*)

    }
  }

  private def assertNested(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_] => Assertion)*): Unit = {
    inside(serializer.asInstanceOf[TypeSerializer[Map[String, _ <:AnyRef]]])  {
      case TypedScalaMapSerializer(array) => array.zipAll(nested.toList, null, null).foreach {
        case ((name, serializer), (expectedName, expectedSerializer)) =>
          name shouldBe expectedName
          expectedSerializer(serializer)
      }
    }
  }

  private def assertMapSerializers(serializer: TypeSerializer[_], nested: (String, TypeSerializer[_])*) = {
    inside(serializer.asInstanceOf[TypeSerializer[Map[String, _ <:AnyRef]]])  {
      case TypedScalaMapSerializer(array) => array.toList shouldBe nested.toList
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

  case class CustomObjectTypeInformation(fields: Map[String, TypeInformation[_]]) extends TypedObjectBasedTypeInformation[CustomTypedObject](fields) {
    override def createSerializer(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[CustomTypedObject] = new CustomObjectTypeSerializer(serializers)
  }

  case class CustomObjectTypeSerializer(override val serializers: Array[(String, TypeSerializer[_])])
    extends TypedObjectBasedTypeSerializer[CustomTypedObject](serializers) with BaseJavaMapBasedSerializer[AnyRef, CustomTypedObject] {

    override def snapshotConfiguration(snapshots: Array[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[CustomTypedObject]
      = new CustomObjectSerializerSnapshot(snapshots)

    override def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[CustomTypedObject]
      = CustomObjectTypeSerializer(serializers)

    override def createInstance(): CustomTypedObject = CustomTypedObject(Collections.emptyMap())

  }

  class CustomObjectSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[CustomTypedObject] {

    def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
      this()
      this.serializersSnapshots = serializers
    }

    override protected def compatibilityRequiresSameKeys: Boolean = true

    override protected def restoreSerializer(restored: Array[(String, TypeSerializer[_])]): TypeSerializer[CustomTypedObject]
      = CustomObjectTypeSerializer(restored)

  }

}
