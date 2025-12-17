package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

class DatabaseEnricherComponentProvider extends ComponentProvider {

  override val providerName: String = "databaseEnricher"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = {
    DatabaseEnricherComponentProvider.create(componentProviderConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}

object DatabaseEnricherComponentProvider {

  def create(config: Config): List[ComponentDefinition] = {
    val docsConfig = DocsConfig(config)
    import docsConfig._

    val componentConfig = config.getConfig("config")
    val dbQueryEnricher = readEnricherConfigIfPresent(componentConfig, "databaseQueryEnricher")
      .map(queryConfig =>
        ComponentDefinition(
          name = queryConfig.name.value,
          component = new DatabaseQueryEnricher(
            queryConfig.dbPool,
            queryConfig.name
          )
        ).withRelativeDocs("Enrichers#databasequeryenricher")
      )
    val dbLookupEnricher = readEnricherConfigIfPresent(componentConfig, "databaseLookupEnricher")
      .map(lookupConfig =>
        ComponentDefinition(
          name = lookupConfig.name.value,
          component = new DatabaseLookupEnricher(lookupConfig.dbPool, lookupConfig.name)
        ).withRelativeDocs("Enrichers#databaselookupenricher")
      )

    List(dbQueryEnricher, dbLookupEnricher).filter(_.isDefined).map(_.get)

  }

  private def readEnricherConfigIfPresent(config: Config, path: String): Option[DbEnricherConfig] = {
    if (config.hasPath(path)) {
      Some(config.as[DbEnricherConfig](path))
    } else {
      None
    }
  }

}
