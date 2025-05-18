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

  def contextToRow(context: Context, validationContext: ValidationContext): Row = {
    val parentContextAsRow = for {
      parentValidationContext <- validationContext.parent
      parentContext           <- context.parentContext
    } yield contextToRow(parentContext, parentValidationContext)
    val row          = Row.withPositions(parentContextAsRow.map(_ => 4).getOrElse(3))
    val variablesRow = encodeVariables(context.variables, validationContext)
    row.setField(0, context.initialId)
    row.setField(1, context.id)
    row.setField(2, variablesRow)
    parentContextAsRow.foreach(row.setField(3, _))
    row
  }

  private def encodeVariables(variables: Map[String, Any], validationContext: ValidationContext): Row = {
    val row = Row.withNames()
    variables.foreach { case (variableName, variableValue) =>
      val encodedValue = validationContext
        .get(variableName)
        .map(ToTableTypeEncoder.encode(variableValue, _))
        .getOrElse(variableValue)
      row.setField(variableName, encodedValue)
    }
    row
  }

  def rowToContext(row: Row): Context = {
    def rowToScalaMap(row: Row): Map[String, AnyRef] = {
      val fieldNames = row.getFieldNames(true).asScala
      fieldNames.map { fieldName =>
        fieldName -> row.getField(fieldName)
      }.toMap
    }
    Context(
      row.getField(0).asInstanceOf[String],
      row.getField(1).asInstanceOf[String],
      rowToScalaMap(row.getField(2).asInstanceOf[Row]),
      Option(row).filter(_.getArity >= 4).map(_.getField(3).asInstanceOf[Row]).map(rowToContext)
    )
  }

  implicit class TypeInformationDetectionExtension(typeInformationDetection: TypeInformationDetection) {

    def contextRowTypeInfo(validationContext: ValidationContext): TypeInformation[_] = {
      val (fieldNames, typeInfos) =
        validationContext.localVariables
          .mapValuesNow(ToTableTypeEncoder.alignTypingResult)
          .mapValuesNow(typeInformationDetection.forType)
          .unzip
      val variablesRow = new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)
      Types.ROW(
        Types.STRING :: Types.STRING :: variablesRow :: validationContext.parent.map(contextRowTypeInfo).toList: _*
      )
    }

  }

}
