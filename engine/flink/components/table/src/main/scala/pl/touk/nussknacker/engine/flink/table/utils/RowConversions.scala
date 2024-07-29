package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object RowConversions {

  import scala.jdk.CollectionConverters._

  def contextToRow(context: Context): Row = {
    val row          = Row.withPositions(context.parentContext.map(_ => 3).getOrElse(2))
    val variablesRow = scalaMapToRow(context.variables)
    row.setField(0, context.id)
    row.setField(1, variablesRow)
    context.parentContext.map(contextToRow).foreach(row.setField(2, _))
    row
  }

  private def scalaMapToRow(map: Map[String, Any]): Row = {
    val row = Row.withNames()
    map.foreach { case (name, value) =>
      row.setField(name, value)
    }
    row
  }

  def rowToContext(row: Row): Context = {
    Context(
      row.getField(0).asInstanceOf[String],
      rowToScalaMap(row.getField(1).asInstanceOf[Row]),
      Option(row).filter(_.getArity >= 3).map(_.getField(2).asInstanceOf[Row]).map(rowToContext)
    )
  }

  private def rowToScalaMap(row: Row): Map[String, AnyRef] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    fields
  }

  implicit class TypeInformationDetectionExtension(typeInformationDetection: TypeInformationDetection) {

    def contextRowTypeInfo(validationContext: ValidationContext): TypeInformation[_] = {
      val (fieldNames, typeInfos) =
        validationContext.localVariables.mapValuesNow(typeInformationDetection.forType).unzip
      val variablesRow = new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)
      Types.ROW(Types.STRING :: variablesRow :: validationContext.parent.map(contextRowTypeInfo).toList: _*)
    }

  }

}
