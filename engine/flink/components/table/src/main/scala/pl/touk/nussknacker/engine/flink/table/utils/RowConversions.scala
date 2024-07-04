package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypedObjectTypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.util

object RowConversions {

  import scala.jdk.CollectionConverters._

  def rowToMap(row: Row): java.util.Map[String, Any] = {
    val fields: Map[String, AnyRef] = rowToScalaMap(row)
    new util.HashMap[String, Any](fields.asJava)
  }

  private def rowToScalaMap(row: Row): Map[String, AnyRef] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    fields
  }

  def mapToRow(map: java.util.Map[String, Any], columnNames: Iterable[String]): Row = {
    val row = Row.withNames()
    columnNames.foreach(columnName => row.setField(columnName, map.get(columnName)))
    row
  }

  private def scalaMapToRow(map: Map[String, Any]): Row = {
    val row = Row.withNames()
    // TODO: add type alignment, e.g. bigint is represented as long by flink tables
    map.foreach { case (name, value) =>
      row.setField(name, value)
    }
    row
  }

  def contextToRow(context: Context): Row = {
    val row          = Row.withPositions(context.parentContext.map(_ => 3).getOrElse(2))
    val variablesRow = scalaMapToRow(context.variables)
    row.setField(0, context.id)
    row.setField(1, variablesRow)
    context.parentContext.map(contextToRow).foreach(row.setField(2, _))
    row
  }

  def rowToContext(row: Row): Context = {
    Context(
      row.getField(0).asInstanceOf[String],
      rowToScalaMap(row.getField(1).asInstanceOf[Row]),
      Option(row).filter(_.getArity >= 3).map(_.getField(2).asInstanceOf[Row]).map(rowToContext)
    )
  }

  implicit class TypeInformationDetectionExtension(typeInformationDetection: TypeInformationDetection) {

    def rowTypeInfoWithColumnsInGivenOrder(
        recordTypingResult: TypedObjectTypingResult,
        columnNames: Iterable[String]
    ): RowTypeInfo = {
      val (fieldNames, typeInfos) = columnNames.flatMap { columnName =>
        recordTypingResult.fields
          .get(columnName)
          .map(typeInformationDetection.forType)
          .map(columnName -> _)
      }.unzip
      new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)
    }

    def contextRowTypeInfo(validationContext: ValidationContext): TypeInformation[_] = {
      val (fieldNames, typeInfos) =
        validationContext.localVariables.mapValuesNow(typeInformationDetection.forType).unzip
      val variablesRow = new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)
      Types.ROW(Types.STRING :: variablesRow :: validationContext.parent.map(contextRowTypeInfo).toList: _*)
    }

  }

}
