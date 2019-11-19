package pl.touk.nussknacker.engine.api.dict.static

import pl.touk.nussknacker.engine.api.dict.{DictDefinition, ReturningKeyWithoutTransformation}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass}

import scala.reflect.ClassTag

/**
 * It is DictDefinition which contains embedded label <> key transformation.
 */
trait StaticDictDefinition extends DictDefinition {

  def labelByKey: Map[String, String]

  lazy val keyByLabel: Map[String, String] = labelByKey.map(_.swap)

}

private[static] case class SimpleDictDefinition(labelByKey: Map[String, String]) extends StaticDictDefinition with ReturningKeyWithoutTransformation

private[static] case class EnumDictDefinition(valueClass: ClazzRef, private val enumValueByName: Map[String, Any]) extends StaticDictDefinition {

  override def labelByKey: Map[String, String] = enumValueByName.keys.map(name => name -> name).toMap

  // we don't need to tag it because value class is enough to recognize type
  override def valueType(dictId: String): SingleTypingResult = TypedClass(valueClass)

  override def value(key: String): Any = enumValueByName(key)

}

object StaticDictDefinition {

  def apply(labelByKey: Map[String, String]): StaticDictDefinition =
    SimpleDictDefinition(labelByKey)

  def forJavaEnum[T <: Enum[_]](javaEnumClass: Class[T]): StaticDictDefinition = {
    val enumValueByName = javaEnumClass.getEnumConstants.map(e => e.name() -> e).toMap
    EnumDictDefinition(ClazzRef(javaEnumClass), enumValueByName)
  }

  def forScalaEnum[T <: Enumeration](scalaEnum: Enumeration): ScalaEnumTypedDictBuilder[T] = new ScalaEnumTypedDictBuilder[T](scalaEnum)

  class ScalaEnumTypedDictBuilder[T <: Enumeration](scalaEnum: Enumeration) {
    def withValueClass[V <: T#Value : ClassTag]: StaticDictDefinition = {
      val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
      EnumDictDefinition(ClazzRef(implicitly[ClassTag[V]].runtimeClass), enumValueByName)
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
  def forScalaEnum(scalaEnum: Enumeration, valueClass: Class[_]): StaticDictDefinition = {
    val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
    EnumDictDefinition(ClazzRef(valueClass), enumValueByName)
  }

  def enumDictId(valueClass: Class[_]) = s"enum:${valueClass.getName}"

}