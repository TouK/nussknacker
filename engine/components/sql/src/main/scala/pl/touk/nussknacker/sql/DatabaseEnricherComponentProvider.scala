package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.sql.db.schema.JdbcMetaDataProviderFactory
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  private val factory = new JdbcMetaDataProviderFactory()

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val componentConfig = config.getConfig("config")
    val queryConfig = componentConfig.as[DbEnricherConfig]("databaseQueryEnricher")
    val lookupConfig = componentConfig.as[DbEnricherConfig]("databaseLookupEnricher")
    val dbQueryEnrichers = ComponentDefinition(name = queryConfig.name, component = new DatabaseQueryEnricher(queryConfig.dbPool, factory.getMetaDataProvider(queryConfig.dbPool)))
    val dbLookupEnrichers = ComponentDefinition(name = lookupConfig.name, component = new DatabaseLookupEnricher(lookupConfig.dbPool, factory.getMetaDataProvider(lookupConfig.dbPool)))
    List(dbQueryEnrichers, dbLookupEnrichers)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true
}
