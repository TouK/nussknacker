package pl.touk.nussknacker.engine.sql

import java.util
import java.util.Date

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import cats.data._
import cats.implicits._

object CreateColumnModel {

  private[sql] val getListInnerType: TypingResult => Validated[NotAListMessage, TypingResult] = {
    case t@Typed(klasses) if klasses.size == 1 =>
      val headClass = klasses.head
      val isAssignableFromKlass: Class[_] => Boolean =
        _.isAssignableFrom(klasses.head.klass)
      if (isAssignableFromKlass(classOf[Traversable[_]])
        || isAssignableFromKlass(classOf[util.Collection[_]]))
        headClass.params.headOption match {
          case Some(typ) => typ.valid
          case None => notAList(t)
        }
      else
        notAList(t)
    case t => notAList(t)
  }

  def apply(typingResult: TypingResult): Validated[InvalidateMessage, ColumnModel] = {
    getListInnerType(typingResult).andThen {
      case TypedClassColumnModel(c) =>
        c.valid
      case TypedMapColumnModel(c) =>
        c.valid
      case Unknown =>
        UnknownInner.invalid
    }
  }

  sealed trait InvalidateMessage

  case class NotAListMessage(typingResult: TypingResult) extends InvalidateMessage

    def notAList(typingResult: TypingResult): Validated[NotAListMessage, TypingResult] = NotAListMessage(typingResult).invalid

  object UnknownInner extends InvalidateMessage

  object MapSqlType {
    //TODO: handle cases java.Integer, LocalDateTime, LocalDate, itd.
    val STRING: ClazzRef = ClazzRef[String]
    val INTEGER: ClazzRef = ClazzRef[Int]
    val NUMBER: ClazzRef = ClazzRef[Number]
    val DATE: ClazzRef = ClazzRef[Date]
    val LONG: ClazzRef = ClazzRef[Long]
    val DOUBLE: ClazzRef = ClazzRef[Double]
    val BOOLEAN: ClazzRef = ClazzRef[Boolean]

    def unapply(arg: ClazzRef): Option[SqlType] = {
      import SqlType._
      arg match {
        case `STRING` => Some(Varchar)
        case `NUMBER` |`INTEGER` | `DOUBLE` | `LONG` => Some(Numeric)
        case `BOOLEAN` => Some(Bool)
        case `DATE` => Some(SqlType.Date)
        case _ => None
      }
    }
  }

}
