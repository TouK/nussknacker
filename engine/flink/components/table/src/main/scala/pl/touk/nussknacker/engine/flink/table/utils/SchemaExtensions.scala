package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.DataTypes.FIELD
import org.apache.flink.table.catalog.{Column, ResolvedSchema}
import org.apache.flink.table.types.utils.DataTypeUtils.removeTimeAttribute

import scala.jdk.CollectionConverters._

object SchemaExtensions {

  implicit class ColumnExtensions(column: Column) {

    def toPhysical: Column.PhysicalColumn = {
      Column.physical(column.getName, column.getDataType).withComment(column.getComment.orElse(null))
    }

  }

  implicit class ResolvedSchemaExtensions(resolvedSchema: ResolvedSchema) {

    def withColumns(columns: java.util.List[Column]): ResolvedSchema = {
      new ResolvedSchema(
        columns,
        resolvedSchema.getWatermarkSpecs,
        resolvedSchema.getPrimaryKey.orElse(null)
      )
    }

    // This is a copy-paste of toRowDataType is public and returns fields instead of DataType
    // The original method is used in toSourceRowDataType and toSinkRowDataType, but is private
    def toRowDataTypeFields(columnPredicate: Column => Boolean): List[DataTypes.Field] =
      resolvedSchema.getColumns.asScala
        .filter(columnPredicate)
        .map(columnToField)
        .toList

    private def columnToField(column: Column) = FIELD(
      column.getName,
      // only a column in a schema should have a time attribute,
      // a field should not propagate the attribute because it might be used in a
      // completely different context
      removeTimeAttribute(column.getDataType)
    )

  }

}
