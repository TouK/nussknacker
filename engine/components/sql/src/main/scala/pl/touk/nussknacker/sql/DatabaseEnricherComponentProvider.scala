package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val componentConfig = config.getConfig("config")
    val dbQueryEnrichers = componentConfig.as[List[DbEnricherConfig]]("databaseQueryEnrichers").map { dbEnricherConfig =>
      ComponentDefinition(
        name = dbEnricherConfig.name,
        component = new DatabaseQueryEnricher(dbEnricherConfig.dbPool))
    }
    val dbLookupEnrichers = componentConfig.as[List[DbEnricherConfig]]("databaseLookupEnrichers").map { dbEnricherConfig =>
      ComponentDefinition(
        name = dbEnricherConfig.name,
        component = new DatabaseLookupEnricher(dbEnricherConfig.dbPool))
    }
    dbQueryEnrichers ++ dbLookupEnrichers
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true
}
