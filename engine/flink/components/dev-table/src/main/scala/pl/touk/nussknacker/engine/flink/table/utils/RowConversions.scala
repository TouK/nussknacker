package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.types.Row

object RowConversions {

  import scala.jdk.CollectionConverters._

  def rowToMap(row: Row): java.util.Map[String, Any] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    new java.util.HashMap[String, Any](fields.asJava)
  }

}
