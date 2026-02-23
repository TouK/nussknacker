package pl.touk.nussknacker.engine.flink.table.sql

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.types.DataType
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context => NuContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeEncoder
import pl.touk.nussknacker.engine.flink.table.utils.simulateddatatype.ToSimulatedDataTypeConverter

final case class VariableSchema(name: String, typingResult: TypingResult)

final case class QueryContextSchema(variables: List[VariableSchema]) {

  private lazy val rowAlignedColumns: List[VariableSchema] =
    variables.map(col => col.copy(typingResult = ToTableTypeEncoder.alignTypingResult(col.typingResult)))

  lazy val rowAlignedDataType: DataType = {
    val fields = rowAlignedColumns.map(col =>
      DataTypes.FIELD(
        col.name,
        ToSimulatedDataTypeConverter.toDataType(col.typingResult)
      )
    )
    DataTypes.ROW(fields: _*)
  }

  lazy val rowAlignedTypeInformation: TypeInformation[Row] = {
    val fieldNames = rowAlignedColumns.map(_.name).toArray
    val fieldsTypeInfo = rowAlignedColumns
      .map(col => TypeInformationDetection.instance.forType(col.typingResult))
      .toArray[TypeInformation[_]]
    Types.ROW_NAMED(fieldNames, fieldsTypeInfo: _*)
  }

  def tableSchemaWithEventTime: Schema = Schema
    .newBuilder()
    .fromRowDataType(rowAlignedDataType)
    .columnByMetadata(QueryContextSchema.eventTimeColumn, "TIMESTAMP_LTZ(3)", "rowtime", true)
    .watermark(QueryContextSchema.eventTimeColumn, "SOURCE_WATERMARK()")
    .build()

}

object QueryContextSchema {
  val contextTableName = "context"
  val eventTimeColumn  = "event_time"

  def fromValidationContext(validationContext: ValidationContext): QueryContextSchema = {
    val contextColumns = validationContext.localVariables.toList.map { case (variableName, typingResult) =>
      VariableSchema(variableName, typingResult)
    }
    QueryContextSchema(contextColumns)
  }

  implicit class NuContextExtension(context: NuContext) {

    def toRow(schema: QueryContextSchema): Row = {
      val row = Row.withNames()
      schema.variables.foreach { column =>
        val value = context.variables.get(column.name).orNull
        row.setField(column.name, ToTableTypeEncoder.encode(value, column.typingResult))
      }
      row
    }

  }

}
