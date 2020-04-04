package pl.touk.nussknacker.engine.sql.preparevalues

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.spel.internal.propertyAccessors
import pl.touk.nussknacker.engine.sql.TimestampUtils

import scala.annotation.tailrec

private[preparevalues] trait ReadObjectField {
  def readField(obj:Any, name: String) : Any
}

private[preparevalues] object ReadObjectField extends ReadObjectField {

  //we do it with spring accessors, because field name extraction is spel-compatible, so here we should also respect same rules
  private val accessors = propertyAccessors.configured()

  private val ec = new StandardEvaluationContext()

  override def readField(obj:Any, name: String): Any = {
    val extracted = accessors
      .filter(classes => Option(classes.getSpecificTargetClasses).forall(_.exists(_.isInstance(obj))))
      .find(_.canRead(ec, obj, name))
      .map(_.read(ec, obj, name))
      .getOrElse(throw ClassValueNotFound(obj, name))
      .getValue
    additionalConversions(extracted)
  }


  @tailrec
  private def additionalConversions(value: Any): Any = {
    value match {
      case bd: scala.math.BigDecimal =>
        bd.bigDecimal
      case None =>
        null
      case Some(v) =>
        additionalConversions(v)
      case ldt: LocalDateTime =>
        TimestampUtils.toTimestamp(ldt)
      case other =>
        other
    }
  }

  case class ClassValueNotFound(obj: Any, name: String)
    extends IllegalArgumentException(s"$obj hasn't value $name")

}