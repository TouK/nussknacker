package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.{Schema, TableDescriptor}
import pl.touk.nussknacker.engine.flink.table.DataSourceConfig

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
