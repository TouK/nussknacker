package pl.touk.nussknacker.engine.flink.table.generic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.generic.TableDefinitionsReader.{
  extractTablesFromFlinkRuntime,
  readFileFromResources
}

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.util.{Failure, Success, Try}

class GenericTableComponentProvider extends ComponentProvider {

  override def providerName: String = "genericTable"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {

    val sqlStatements = readFileFromResources()

    val dataSources = extractTablesFromFlinkRuntime(sqlStatements)

    val sources = dataSources.map(ds => {
      ComponentDefinition(prepareComponentName(ds.name), new GenericTableSourceFactory(ds))
    })

    sources
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

  private def prepareComponentName(dataSourceName: String) = s"tableSource-$dataSourceName"

}

object TableDefinitionsReader extends LazyLogging {

  private val separator                = ";\\s*"
  private val tablesDefinitionFileName = "tables-definition.sql"
  private val defaultCatalogName       = "default_catalog"
  private val defaultDatabase          = "default_database"
  private val connectorKey             = "connector"

  def readFileFromResources(): List[String] =
    Try {
      scala.io.Source
        .fromResource(tablesDefinitionFileName)
        .mkString
        .split(separator)
        .filter(_.trim.startsWith("CREATE TABLE"))
        .toList
    } match {
      case Failure(exception) =>
        logger.warn(s"Couldn't parse sql tables definition: $exception")
        List.empty
      case Success(value) => value
    }

  def extractTablesFromFlinkRuntime(createTableStatements: List[String]): List[DataSourceTable] = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    val catalog = tableEnv.getCatalog(defaultCatalogName).asScala match {
      case Some(value) => value
      case None =>
        throw new IllegalStateException(
          "Default catalog was not found during parsing of sql for generic table components."
        )
    }

    // TODO local: table results show if adding table was successful - we may use this to handle failures
    val tableResults = createTableStatements.map { s =>
      tableEnv.executeSql(s)
    }

    // TODO local: find if there is a simpler way to get granular table metadata like connector
    // Lower level api - Here we have access to unresolved schema and more metadata
    val tables = tableEnv
      .listTables()
      .map { tableName =>
        {
          val tablePath                                   = new ObjectPath(defaultDatabase, tableName)
          val tableWithUnresolvedSchema: CatalogBaseTable = catalog.getTable(tablePath)
          val connectorName = Try(tableWithUnresolvedSchema.getOptions.get(connectorKey))
            .getOrElse(throw new IllegalStateException(s"Table $tableName did not have connector specified."))
          tableName -> connectorName
        }
      }
      .toList

    // Higher level api - Here we don't seem to have access to unresolved schema but we have access to resolved schema
    val dataSourceTables = tables.map { case (tableName: String, connector: String) =>
      val tableWithResolvedSchema = tableEnv.from(tableName)
      val columns =
        tableWithResolvedSchema.getResolvedSchema.getColumns.asScala.map(c => Column(c.getName, c.getDataType)).toList
      DataSourceTable(tableName, connector, Schema(columns))
    }

    // TODO local: find how to close the environment - do we need to drop the tables? is there a way to close it explicitly?
    tableEnv.listTables().map { tableName =>
      tableEnv.executeSql(
        s"DROP TABLE $tableName"
      )
    }

    dataSourceTables
  }

}

final case class DataSourceTable(name: String, connector: String, schema: Schema)
final case class Schema(columns: List[Column])
final case class Column(name: String, dataType: DataType)
