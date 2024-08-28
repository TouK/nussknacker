package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.logical.{ArrayType, LogicalType, MapType, MultisetType, RowType}
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.jdk.CollectionConverters._

object DataTypesExtensions {

  implicit class DataTypeExtension(dataType: DataType) {

    def toLogicalRowTypeUnsafe: RowType = dataType.getLogicalType.toRowTypeUnsafe

  }

  implicit class LogicalTypeExtension(logicalType: LogicalType) {

    def toRowTypeUnsafe: RowType = logicalType match {
      case rowType: RowType => rowType
      case other            => throw new IllegalArgumentException(s"Not a RowType: $other")
    }

    def toTypingResult: TypingResult = {
      logicalType match {
        case array: ArrayType =>
          Typed.genericTypeClass(classOf[Array[AnyRef]], List(array.getElementType.toTypingResult))
        case row: RowType => row.toTypingResult
        case map: MapType =>
          Typed
            .genericTypeClass[java.util.Map[_, _]](List(map.getKeyType.toTypingResult, map.getValueType.toTypingResult))
        case multiset: MultisetType =>
          TypedMultiset(multiset.getElementType.toTypingResult)
        // TODO: handle complex types like maps, lists, rows, raws and types alignment
        case _ => Typed(logicalType.getDefaultConversion)
      }
    }

  }

  implicit class RowTypeExtension(rowType: RowType) {

    def toTypingResult: TypedObjectTypingResult = {
      val fieldsTypes = rowType.getFields.asScala.map { field =>
        field.getName -> field.getType.toTypingResult
      }
      Typed.record(fieldsTypes, Typed.typedClass[Row])
    }

  }

  implicit class TypingResultExtension(typingResult: TypingResult) {

    def toDataType: DataType = {
      val typeInfo = TypeInformationDetection.instance.forType(typingResult)
      TypeInfoDataTypeConverter.toDataType(DataTypeFactoryHolder.instance, typeInfo)
    }

  }

}
