package pl.touk.nussknacker.engine.sql.columnmodel

import java.time.LocalDateTime
import java.util.Date

import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.sql.{ColumnModel, SqlType}

object CreateColumnModel {

  def apply(typingResult: TypingResult): Validated[InvalidateMessage, ColumnModel] = {
    getListInnerType(typingResult).andThen {
      case typed: TypedClass =>
        TypedClassColumnModel.create(typed).valid
      case typedMap: TypedObjectTypingResult =>
        TypedMapColumnModel.create(typedMap).valid
      case _: TypedUnion | Unknown | _:TypedDict | _:TypedTaggedValue =>
        UnknownInner.invalid
    }
  }

  private[columnmodel] val getListInnerType: TypingResult => Validated[InvalidateMessage, TypingResult] = {
    case typedClass: SingleTypingResult =>
      val isCollection =
        classOf[Traversable[_]].isAssignableFrom(typedClass.objType.klass) ||
        classOf[java.util.Collection[_]].isAssignableFrom(typedClass.objType.klass)
      if (isCollection)
        typedClass.objType.params.headOption match {
          case Some(typ) => typ.valid
          case None => UnknownInner.invalid
        }
      else {
        notAList(typedClass)
      }
    case t => notAList(t)
  }

  sealed trait InvalidateMessage

  case class NotAListMessage(typingResult: TypingResult) extends InvalidateMessage

  def notAList(typingResult: TypingResult): Validated[NotAListMessage, TypingResult] = NotAListMessage(typingResult).invalid

  object UnknownInner extends InvalidateMessage

  object ClazzToSqlType extends LazyLogging {
    val STRING: TypedClass = TypedClass[String]
    val INTEGER: TypedClass = TypedClass[Int]
    val LONG: TypedClass = TypedClass[Long]
    val DOUBLE: TypedClass = TypedClass[Double]
    val BIG_DECIMAL: TypedClass = TypedClass[BigDecimal]
    val J_BIG_DECIMAL: TypedClass = TypedClass[java.math.BigDecimal]
    val J_LONG: TypedClass = TypedClass[java.lang.Long]
    val J_INTEGER: TypedClass = TypedClass[java.lang.Integer]
    val J_DOUBLE: TypedClass = TypedClass[java.lang.Double]
    val J_BOOLEAN: TypedClass = TypedClass[java.lang.Boolean]
    val BOOLEAN: TypedClass = TypedClass[Boolean]
    val NUMBER: TypedClass = TypedClass[Number]
    val DATE: TypedClass = TypedClass[Date]
    val LOCAL_DATE_TIME: TypedClass = TypedClass[LocalDateTime]

    def convert(name: String, arg: TypedClass, className: String): Option[SqlType] = {
      import SqlType._
      arg match {
        case STRING =>
          Some(Varchar)
        case NUMBER =>
          Some(Decimal)
        case DOUBLE | BIG_DECIMAL |
           J_DOUBLE | J_BIG_DECIMAL =>
          Some(Decimal)
        case INTEGER | LONG |
           J_INTEGER | J_LONG  =>
          Some(Numeric)
        case BOOLEAN |
             J_BOOLEAN =>
          Some(Bool)
          //TODO: other date types?
        case DATE | LOCAL_DATE_TIME =>
          Some(SqlType.Date)
        case a =>
          logger.warn(s"No mapping for name: $name in $className and type $a")
          None
      }
    }
  }

}
