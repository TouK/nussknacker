package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    DatabaseEnricherComponentProvider.create(config)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}

object DatabaseEnricherComponentProvider {

  def create(config: Config): List[ComponentDefinition] = {
    val docsConfig = DocsConfig(config)
    import docsConfig._

    val factory         = new MetaDataProviderFactory()
    val componentConfig = config.getConfig("config")
    val dbQueryEnricher = readEnricherConfigIfPresent(componentConfig, "databaseQueryEnricher")
      .map(queryConfig =>
        ComponentDefinition(
          name = queryConfig.name,
          component = new DatabaseQueryEnricher(
            queryConfig.dbPool,
            factory.create(queryConfig.dbPool)
          ),
          label = Some("db query")
        ).withRelativeDocs("Enrichers#databasequeryenricher")
      )
    val dbLookupEnricher = readEnricherConfigIfPresent(componentConfig, "databaseLookupEnricher")
      .map(lookupConfig =>
        ComponentDefinition(
          name = lookupConfig.name,
          component = new DatabaseLookupEnricher(lookupConfig.dbPool, factory.create(lookupConfig.dbPool)),
          label = Some("db lookup")
        ).withRelativeDocs("Enrichers#databaselookupenricher")
      )

    List(dbQueryEnricher, dbLookupEnricher).filter(_.isDefined).map(_.get)

  }

  private def readEnricherConfigIfPresent(config: Config, path: String): Option[DbEnricherConfig] =
    if (config.hasPath(path)) {
      Some(config.as[DbEnricherConfig](path))
    } else {
      None
    }

}
