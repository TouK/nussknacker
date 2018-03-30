package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.TypedMap

trait ReadObjectField {
  def readField(obj:Any, name: String) : Any
}

object ReadObjectField extends ReadObjectField {

  override def readField(obj:Any, name: String): Any = {
    obj match {
      case TypedMap(aMap) =>
        aMap.collectFirst { case (key, value) if key.toLowerCase == name.toLowerCase => value }
          .getOrElse(name, throw ClassValueNotFount(obj, name))
      case obj: Any =>
      //FIXME: doesn't work for fields,javabeans, etc.
        val value = obj.getClass.getMethods
        .find(_.getName.equalsIgnoreCase(name))
        .getOrElse(throw ClassValueNotFount(obj, name))
        .invoke(obj)
        toJava(value)
    }
  }

  private def toJava(value: Any): Any = {
    value match {
      case bd: scala.math.BigDecimal =>
        bd.bigDecimal
      case None =>
        null
      case Some(v) =>
        toJava(v)
      case other =>
        other
    }
  }

  case class ClassValueNotFount(obj: Any, name: String)
    extends IllegalArgumentException(s"$obj hasn't value $name")

}