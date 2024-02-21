package pl.touk.nussknacker.engine.flink.table.generic

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.generic.DataSourceSqlStatementsReader.defaultDataSourceDefinitionFileName
import pl.touk.nussknacker.engine.flink.table.generic.SqlDataSourceExtractor.extractTablesFromFlinkRuntime

class GenericTableComponentProvider extends ComponentProvider {

  override def providerName: String = "genericTable"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val dataSources = extractDataSourcesFromConfigFile()
    val sources = dataSources.map(ds => {
      ComponentDefinition(prepareComponentName(ds.name), new GenericTableSourceFactory(ds))
    })
    sources
  }

  private def extractDataSourcesFromConfigFile(): List[DataSourceTable] = {
    val sqlStatements = DataSourceSqlStatementsReader.readFileFromResources(defaultDataSourceDefinitionFileName)
    extractTablesFromFlinkRuntime(sqlStatements)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

  private def prepareComponentName(dataSourceName: String) = s"tableSource-$dataSourceName"

}
