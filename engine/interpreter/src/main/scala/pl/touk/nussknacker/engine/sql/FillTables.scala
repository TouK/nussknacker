package pl.touk.nussknacker.engine.sql

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.immutable


object FillTables {
  //TODO: replace with sequence and imports
  private def transformValidated(validatedTables: List[Validated[NotAListException, (String, Table)]])
  : ValidatedNel[NotAListException, Map[String, Table]] = {
    val errors = validatedTables.collect{
      case Invalid(nale) => nale
    }
    errors match {
      case head :: tail => NonEmptyList(head, tail).invalid
      case Nil => validatedTables.collect{
        case Valid((name, table))=> name->table
      }.toMap.validNel
    }
  }

  def apply(
             values: Map[String, Any],
             colModels: Map[String, ColumnModel],
             readObjectField: ReadObjectField
           ): ValidatedNel[NotAListException, Map[String, Table]] = {
    val validatedList = values
      .filterKeys { key =>
        colModels.keys.toList.contains(key)
      } map { case (key, any) =>
      val columnModel = colModels(key)
      marshall(key, readObjectField, columnModel, any)
        .map { list =>
          key -> Table(columnModel, list)
        }
    }
    transformValidated(validatedList.toList)
  }

  private[sql] def marshall(name: String,
                            readObjectField: ReadObjectField,
                            columnModel: ColumnModel,
                            any: Any
                           ): Validated[NotAListException, List[List[Any]]] =
    any match {
      case list: Traversable[Any] =>
        list.map { listElement =>
          columnModel.columns.map {
            _.name
          } map { fieldName =>
            readObjectField.readField(listElement, fieldName)
          }
        }.toList.valid
      case javaCollection: java.util.Collection[_] =>
        marshall(name, readObjectField, columnModel, javaCollection.asScala.toList)
      case _ =>
        NotAListException(name, any).invalid
    }

  case class NotAListException(name: String, any: Any)

}
