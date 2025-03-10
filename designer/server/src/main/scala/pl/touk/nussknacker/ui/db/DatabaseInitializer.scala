package pl.touk.nussknacker.ui.db

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.internal.database.hsqldb.HSQLDBDatabaseType
import org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabaseType

object DatabaseInitializer {

  def initDatabase(path: String, config: Config): Unit = {
    configureFlyway(path, config).load().migrate()
  }

  private[db] def configureFlyway(path: String, config: Config): FluentConfiguration = {
    import net.ceedubs.ficus.Ficus._

    val configDb = config.getConfig(path)
    configureFlyway(
      configDb.as[String]("url"),
      configDb.as[String]("user"),
      configDb.as[String]("password"),
      configDb.getAs[String]("schema")
    )
  }

  private def configureFlyway(
      url: String,
      user: String,
      password: String,
      schema: Option[String]
  ): FluentConfiguration = {
    Flyway
      .configure()
      .failOnMissingLocations(true)
      .locations(
        (url match {
          case url if (new HSQLDBDatabaseType).handlesJDBCUrl(url) => Array("db/migration/hsql", "db/migration/common")
          case url if (new PostgreSQLDatabaseType).handlesJDBCUrl(url) =>
            Array("db/migration/postgres", "db/migration/common")
          case _ =>
            throw new IllegalArgumentException(s"Unsupported database url: $url. Use either PostgreSQL or HSQLDB.")
        }): _*
      )
      .dataSource(url, user, password)
      .schemas(schema.toArray: _*)
      .baselineOnMigrate(true)
  }

}
