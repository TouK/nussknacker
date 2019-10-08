package pl.touk.nussknacker.engine.api.typed.dict

import pl.touk.nussknacker.engine.api.typed.ClazzRef

import scala.reflect.ClassTag

trait TypedDictInstance {

  def dictId: String

  def labelByKey: Map[String, String]

  def runtimeClass: ClazzRef

  def value(key: String): Any

}

case class SimpleTypedDictInstance(dictId: String, labelByKey: Map[String, String]) extends TypedDictInstance {

  override def value(key: String): Any = key

  override def runtimeClass: ClazzRef = ClazzRef[String]

}

case class EnumDictInstance(runtimeClass: ClazzRef, enumValueByName: Map[String, Any]) extends TypedDictInstance {

  override def dictId: String = runtimeClass.refClazzName

  override def labelByKey: Map[String, String] = enumValueByName.keys.map(name => name -> name).toMap

  override def value(key: String): Any = enumValueByName(key)

}

object TypedDictInstance {

  def apply(dictId: String, labelByKey: Map[String, String]): TypedDictInstance =
    SimpleTypedDictInstance(dictId, labelByKey)

  def forEnum(javaEnumClass: Class[Enum[_]]): TypedDictInstance = {
    val enumValueByName = javaEnumClass.getEnumConstants.map(e => e.name() -> e).toMap
    EnumDictInstance(ClazzRef(javaEnumClass), enumValueByName)
  }

  def forEnum[T <: Enumeration](scalaEnum: Enumeration): ScalaEnumTypedDictBuilder[T] = new ScalaEnumTypedDictBuilder[T](scalaEnum)

  class ScalaEnumTypedDictBuilder[T <: Enumeration](scalaEnum: Enumeration) {
    def withValueClass[V <: T#Value : ClassTag]: TypedDictInstance = {
      val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
      EnumDictInstance(ClazzRef(implicitly[ClassTag[V]].runtimeClass), enumValueByName)
    }
  }


  /**
    * Creates TypedDictInstance with runtimeClass = class Enumeration's Value class and dictId = Enumeration's Value class name
    * You need to define own Enumeration's Value class e.g.:
    * `
    * object SimpleEnum extends Enumeration {
    * class Value(name: String) extends Val(name)
    *
    * val One: Value = new Value("one")
    * val Two: Value = new Value("two")
    * }
    * `
    */
  def forEnum(scalaEnum: Enumeration, valueClass: Class[_]): TypedDictInstance = {
    val enumValueByName = scalaEnum.values.map(e => e.toString -> e).toMap
    EnumDictInstance(ClazzRef(valueClass), enumValueByName)
  }

}