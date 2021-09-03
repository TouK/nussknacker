package pl.touk.nussknacker.sql

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory
import pl.touk.nussknacker.sql.service.{DatabaseLookupEnricher, DatabaseQueryEnricher}

object EnricherComponentFactory {
  def create(config: Config, factory: MetaDataProviderFactory): List[ComponentDefinition] = {
    val componentConfig = config.getConfig("config")
    val queryConfig = componentConfig.as[DbEnricherConfig]("databaseQueryEnricher")
    val lookupConfig = componentConfig.as[DbEnricherConfig]("databaseLookupEnricher")
    val dbQueryEnricher = ComponentDefinition(name = queryConfig.name, component = new DatabaseQueryEnricher(queryConfig.dbPool, factory.create(queryConfig.dbPool)))
    val dbLookupEnricher = ComponentDefinition(name = lookupConfig.name, component = new DatabaseLookupEnricher(lookupConfig.dbPool, factory.create(lookupConfig.dbPool)))
    List(dbQueryEnricher, dbLookupEnricher)
  }
}
