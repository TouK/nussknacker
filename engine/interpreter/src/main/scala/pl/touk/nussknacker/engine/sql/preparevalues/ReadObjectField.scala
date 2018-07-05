package pl.touk.nussknacker.engine.sql.preparevalues

import org.springframework.expression.PropertyAccessor
import org.springframework.expression.spel.support.{ReflectivePropertyAccessor, StandardEvaluationContext}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.{MapPropertyAccessor, ScalaPropertyAccessor, StaticPropertyAccessor, TypedMapPropertyAccessor}

private[preparevalues] trait ReadObjectField {
  def readField(obj:Any, name: String) : Any
}

private[preparevalues] object ReadObjectField extends ReadObjectField {

  //we do it with spring accessors, because field name extraction is spel-compatible, so here we should also respect same rules
  private val accessors = List[PropertyAccessor](TypedMapPropertyAccessor, MapPropertyAccessor, ScalaPropertyAccessor, StaticPropertyAccessor, new ReflectivePropertyAccessor)

  private val ec = new StandardEvaluationContext()

  override def readField(obj:Any, name: String): Any = {
    val extracted = accessors
      .filter(classes => Option(classes.getSpecificTargetClasses).forall(_.exists(_.isInstance(obj))))
      .find(_.canRead(ec, obj, name))
      .map(_.read(ec, obj, name))
      .getOrElse(throw ClassValueNotFound(obj, name))
      .getValue
    toJava(extracted)
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

  case class ClassValueNotFound(obj: Any, name: String)
    extends IllegalArgumentException(s"$obj hasn't value $name")

}