package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.TypedObjectTypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

object RowConversions {

  import scala.jdk.CollectionConverters._

  def rowToMap(row: Row): java.util.Map[String, Any] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    new java.util.HashMap[String, Any](fields.asJava)
  }

  def mapToRow(map: java.util.Map[String, Any], columnNames: Iterable[String]): Row = {
    val row = Row.withNames()
    columnNames.foreach(columnName => row.setField(columnName, map.get(columnName)))
    row
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

  }

}
