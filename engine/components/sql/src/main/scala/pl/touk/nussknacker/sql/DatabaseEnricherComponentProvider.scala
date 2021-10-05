package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val factory = new MetaDataProviderFactory()
    val componentConfig = config.getConfig("config")
    val dbQueryEnricher = readEnricherConfigIfPresent(componentConfig, "databaseQueryEnricher")
      .map(queryConfig => ComponentDefinition(name = queryConfig.name, component = new DatabaseQueryEnricher(queryConfig.dbPool, factory.create(queryConfig.dbPool))))
    val dbLookupEnricher = readEnricherConfigIfPresent(componentConfig, "databaseLookupEnricher")
      .map(lookupConfig => ComponentDefinition(name = lookupConfig.name, component = new DatabaseLookupEnricher(lookupConfig.dbPool, factory.create(lookupConfig.dbPool))))

    List(dbQueryEnricher, dbLookupEnricher).filter(_.isDefined).map(_.get)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  private def readEnricherConfigIfPresent(config: Config, path: String): Option[DbEnricherConfig] =
    if (config.hasPath(path)) {
      Some(config.as[DbEnricherConfig](path))
    } else {
      None
    }
}
