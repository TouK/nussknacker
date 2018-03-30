package pl.touk.nussknacker.engine.sql

import java.util.Date

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult, TypingResult, Unknown}
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

object CreateColumnModel {

  def apply(typingResult: TypingResult): Validated[InvalidateMessage, ColumnModel] = {
    getListInnerType(typingResult).andThen {
      case typed: Typed =>
        TypedClassColumnModel.create(typed).valid
      case typedMap: TypedMapTypingResult =>
        TypedMapColumnModel.create(typedMap).valid
      case Unknown =>
        UnknownInner.invalid
    }
  }

  private[sql] val getListInnerType: TypingResult => Validated[NotAListMessage, TypingResult] = {
    case t@Typed(klasses) if klasses.size == 1 =>
      val headClass = klasses.head
      val isCollection =
        classOf[Traversable[_]].isAssignableFrom(headClass.klass) ||
        classOf[java.util.Collection[_]].isAssignableFrom(headClass.klass)
      if (isCollection)
        headClass.params.headOption match {
          case Some(typ) => typ.valid
          case None => notAList(t)
        }
      else {
        notAList(t)
      }
    case t => notAList(t)
  }

  sealed trait InvalidateMessage

  case class NotAListMessage(typingResult: TypingResult) extends InvalidateMessage

  def notAList(typingResult: TypingResult): Validated[NotAListMessage, TypingResult] = NotAListMessage(typingResult).invalid

  object UnknownInner extends InvalidateMessage

  object ClazzToSqlType extends LazyLogging {
    //TODO: handle cases java.Integer, LocalDateTime, LocalDate, itd.
    val STRING: ClazzRef = ClazzRef[String]
    val INTEGER: ClazzRef = ClazzRef[Int]
    val LONG: ClazzRef = ClazzRef[Long]
    val DOUBLE: ClazzRef = ClazzRef[Double]
    val NUMBER: ClazzRef = ClazzRef[Number]
    val DATE: ClazzRef = ClazzRef[Date]
    val BOOLEAN: ClazzRef = ClazzRef[Boolean]

    def convert(name: String, arg: ClazzRef): Option[SqlType] = {
      import SqlType._
      arg match {
        case `STRING` =>
          Some(Varchar)
        case _ if classOf[java.lang.Number].isAssignableFrom(arg.clazz) =>
          Some(Numeric)
        case `INTEGER` | `DOUBLE` | `LONG`  =>
          Some(Numeric)
        case `BOOLEAN` =>
          Some(Bool)
        case `DATE` =>
          Some(SqlType.Date)
        case a =>
          logger.warn(s"no mapping for name: $name and type $a")
          None
      }
    }
  }

}
