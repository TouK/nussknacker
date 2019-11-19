package pl.touk.nussknacker.engine.api.typed.dict

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedClass}

import scala.reflect.ClassTag

sealed trait TypedDictInstance {

  def dictId: String

  def valueType: SingleTypingResult

  // It should return value in declared type.
  def value(key: String): Any

}

trait StaticTypedDictInstance extends TypedDictInstance {

  def labelByKey: Map[String, String]

}

trait DynamicTypedDictInstance extends TypedDictInstance

trait ReturningKeyWithoutTransformation { self: TypedDictInstance =>

  override def value(key: String): Any = key

  override def valueType: SingleTypingResult = Typed.taggedDictValue(TypedClass[String], dictId)

}

private[nussknacker] case class SimpleTypedDictInstance(dictId: String, labelByKey: Map[String, String]) extends StaticTypedDictInstance with ReturningKeyWithoutTransformation

private[nussknacker] case class SimpleDynamicTypedDictInstance(dictId: String) extends DynamicTypedDictInstance with ReturningKeyWithoutTransformation

private[nussknacker] case class EnumDictInstance(valueClass: ClazzRef, private val enumValueByName: Map[String, Any]) extends StaticTypedDictInstance {

  override def dictId: String = s"enum:${valueClass.clazz.getName}"

  override def labelByKey: Map[String, String] = enumValueByName.keys.map(name => name -> name).toMap

  // we don't need to tag it because value class is enough to recognize type
  override def valueType: SingleTypingResult = TypedClass(valueClass)

  override def value(key: String): Any = enumValueByName(key)

}

object StaticTypedDictInstance {

  def apply(dictId: String, labelByKey: Map[String, String]): StaticTypedDictInstance =
    SimpleTypedDictInstance(dictId, labelByKey)

  def dynamic(dictId: String): DynamicTypedDictInstance =
    SimpleDynamicTypedDictInstance(dictId)

  def forJavaEnum[T <: Enum[_]](javaEnumClass: Class[T]): StaticTypedDictInstance = {
    val enumValueByName = javaEnumClass.getEnumConstants.map(e => e.name() -> e).toMap
    EnumDictInstance(ClazzRef(javaEnumClass), enumValueByName)
  }

  def forScalaEnum[T <: Enumeration](scalaEnum: Enumeration): ScalaEnumTypedDictBuilder[T] = new ScalaEnumTypedDictBuilder[T](scalaEnum)

  class ScalaEnumTypedDictBuilder[T <: Enumeration](scalaEnum: Enumeration) {
    def withValueClass[V <: T#Value : ClassTag]: StaticTypedDictInstance = {
      val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
      EnumDictInstance(ClazzRef(implicitly[ClassTag[V]].runtimeClass), enumValueByName)
    }
  }


  /**
    * Creates TypedDictInstance with runtimeClass = class Enumeration's Value class and dictId based on Enumeration's Value class name
    * You need to define own Enumeration's Value class e.g.:
    * `
    * object SimpleEnum extends Enumeration {
    * class Value(name: String) extends Val(name)
    *
    * val One: Value = new Value("one")
    * val Two: Value = new Value("two")
    * }
    * `
    *
    * WARNING !!!
    *
    * If you use SimpleEnum.Value as type in object serialized by Flink, macro for TypeInformation for scala types doesn't handle it.
    * It is because Flink's `EnumValueTypeInfo` require `Class[T#Value]` but we provide `Class[SomeValue extends T#Value]`
    * and `Class[T]` is invariant in their type `T`. So it is better to use Java enums instead in this place.
    */
  def forScalaEnum(scalaEnum: Enumeration, valueClass: Class[_]): StaticTypedDictInstance = {
    val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
    EnumDictInstance(ClazzRef(valueClass), enumValueByName)
  }

}