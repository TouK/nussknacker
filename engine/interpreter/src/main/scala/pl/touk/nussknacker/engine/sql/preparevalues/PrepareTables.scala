package pl.touk.nussknacker.engine.sql.preparevalues

import cats.data._
import cats.implicits._
import pl.touk.nussknacker.engine.sql.{ColumnModel, Table}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

object PrepareTables {
  def apply(
             values: Map[String, Any],
             colModels: Map[String, ColumnModel],
             readObjectField: ReadObjectField = ReadObjectField
           ): ValidatedNel[NotAListException, Map[String, Table]] = {
    val validatedList = values.filterKeys { key =>
      colModels.keys.toList.contains(key)
    }.map { case (key, any) =>
      val columnModel = colModels(key)
      marshall(key, readObjectField, columnModel, any).map { list =>
        key -> Table(columnModel, list)
      }
    }
    transformValidated(validatedList.toList)
  }

  private def transformValidated(validatedTables: List[Validated[NotAListException, (String, Table)]])
  : ValidatedNel[NotAListException, Map[String, Table]] = {
    validatedTables
      .traverse(_.toValidatedNel)
      .map(_.toMap)
  }

  private[preparevalues] def marshall(name: String,
                            readObjectField: ReadObjectField,
                            columnModel: ColumnModel,
                            any: Any
                           ): Validated[NotAListException, List[List[Any]]] =
    any match {
      case list: Traversable[Any] =>
        list.map { listElement =>
          columnModel.columns.map { column =>
            val fieldName = column.name
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
