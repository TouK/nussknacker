package pl.touk.nussknacker.engine.api.dict.embedded

import pl.touk.nussknacker.engine.api.dict.{DictDefinition, ReturningKeyWithoutTransformation}
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedClass}

import scala.reflect.ClassTag

/**
 * It is DictDefinition which contains embedded label <> key transformation.
 */
trait EmbeddedDictDefinition extends DictDefinition {

  def labelByKey: Map[String, String]

  lazy val keyByLabel: Map[String, String] = labelByKey.map(_.swap)

}

private[embedded] case class SimpleDictDefinition(labelByKey: Map[String, String]) extends EmbeddedDictDefinition with ReturningKeyWithoutTransformation

private[embedded] case class EnumDictDefinition(valueClass: TypedClass, private val enumValueByName: Map[String, Any]) extends EmbeddedDictDefinition {

  override def labelByKey: Map[String, String] = enumValueByName.keys.map(name => name -> name).toMap

  // we don't need to tag it because value class is enough to recognize type
  override def valueType(dictId: String): SingleTypingResult = valueClass

  override def value(key: String): Any = enumValueByName(key)

}

object EmbeddedDictDefinition {

  def apply(labelByKey: Map[String, String]): EmbeddedDictDefinition = {
    checkLabelsAreUnique(labelByKey)
    SimpleDictDefinition(labelByKey)
  }

  private def checkLabelsAreUnique(labelByKey: Map[String, String]): Unit = {
    val labels = labelByKey.values.toList
    val duplicatedValues = labels.diff(labels.distinct).distinct
    assert(duplicatedValues.isEmpty, s"Duplicated labels for dict: $labels")
  }

  def forJavaEnum[T <: Enum[_]](javaEnumClass: Class[T]): EmbeddedDictDefinition = {
    val enumValueByName = javaEnumClass.getEnumConstants.map(e => e.name() -> e).toMap
    EnumDictDefinition(Typed.typedClass(javaEnumClass), enumValueByName)
  }

  def forScalaEnum[T <: Enumeration](scalaEnum: Enumeration): ScalaEnumTypedDictBuilder[T] = new ScalaEnumTypedDictBuilder[T](scalaEnum)

  class ScalaEnumTypedDictBuilder[T <: Enumeration](scalaEnum: Enumeration) {
    def withValueClass[V <: T#Value : ClassTag]: EmbeddedDictDefinition = {
      val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
      EnumDictDefinition(Typed.typedClass[V], enumValueByName)
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
  def forScalaEnum(scalaEnum: Enumeration, valueClass: Class[_]): EmbeddedDictDefinition = {
    val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
    EnumDictDefinition(Typed.typedClass(valueClass), enumValueByName)
  }

  def enumDictId(valueClass: Class[_]) = s"enum:${valueClass.getName}"

}