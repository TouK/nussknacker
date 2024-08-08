package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.logical.{LogicalType, RowType}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.jdk.CollectionConverters._

object DataTypesConversions {

  implicit class DataTypeConverter(dataType: DataType) {

    def toLogicalRowTypeUnsafe: RowType = dataType.getLogicalType.toRowTypeUnsafe

  }

  implicit class LogicalTypeConverter(logicalType: LogicalType) {

    def toRowTypeUnsafe: RowType = logicalType match {
      case rowType: RowType => rowType
      case other            => throw new IllegalArgumentException(s"Not a RowType: $other")
    }

    def toTypingResult: TypingResult = {
      logicalType match {
        case row: RowType => row.toTypingResult
        // TODO: handle complex types like maps, lists, rows, raws and types alignment
        case _ => Typed.typedClass(logicalType.getDefaultConversion)
      }
    }

  }

  implicit class RowTypeConverter(rowType: RowType) {

    def toTypingResult: TypedObjectTypingResult = {
      val fieldsTypes = rowType.getFields.asScala.map { field =>
        field.getName -> field.getType.toTypingResult
      }
      Typed.record(fieldsTypes, Typed.typedClass[Row])
    }

  }

}
