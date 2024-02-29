package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.{Schema, TableDescriptor}
import org.apache.flink.types.Row

object TableUtils {

  def buildTableDescriptor(config: DataSourceConfig, schema: Schema): TableDescriptor = {
    val sinkTableDescriptorBuilder = TableDescriptor
      .forConnector(config.connector)
      .format(config.format)
      .schema(schema)
    config.options.foreach { case (key, value) =>
      sinkTableDescriptorBuilder.option(key, value)
    }
    sinkTableDescriptorBuilder.build()
  }

}

object RowConversions {

  import scala.jdk.CollectionConverters._

  def rowToMap(row: Row): java.util.Map[String, Any] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    new java.util.HashMap[String, Any](fields.asJava)
  }

}
