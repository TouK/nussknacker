package pl.touk.nussknacker.engine.flink.table.io.definition.discovery

import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.Catalog
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinition
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinition.DataDefinitionRegistrationResultExtension

import scala.jdk.OptionConverters.RichOptional

object CatalogDiscovery {

  def discoverCatalog(env: TableEnvironment, flinkDataDefinition: FlinkDataDefinition, catalogName: String): Catalog = {
    flinkDataDefinition.registerIn(env).orFail
    env
      .getCatalog(catalogName)
      .toScala
      .getOrElse(
        throw new IllegalStateException(
          s"Table extractor could not locate a created catalog with id: ${catalogName}"
        )
      )
  }

}
