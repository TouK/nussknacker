package pl.touk.nussknacker.ui.process.periodic.legacy.db

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabaseType
import pl.touk.nussknacker.ui.db.{DbRef, NuExPostgresProfile, NuHsqldbProfile, NuJdbcProfile}
import slick.jdbc.{HsqldbProfile, JdbcBackend, PostgresProfile}

object LegacyDbInitializer extends LazyLogging {

  def init(configDb: Config): (JdbcBackend.DatabaseDef, NuJdbcProfile) = {
    import net.ceedubs.ficus.Ficus._
    val url        = configDb.as[String]("url")
    val schemaName = configDb.getAs[String]("schema").getOrElse(DbRef.defaultSchemaName)
    val profile    = chooseDbProfile(url, schemaName)
    logger.info("Applying db migrations")

    // we want to set property on FluentConfiguration only if there is property in config
    implicit class OptionalConfig(config: FluentConfiguration) {
      def setOptional[A](name: String, setAction: (FluentConfiguration, A) => FluentConfiguration)(
          implicit reader: ValueReader[Option[A]]
      ): FluentConfiguration = {
        configDb.getAs[A](name).fold(config)(setAction(config, _))
      }
    }

    Flyway
      .configure()
      .locations(
        (profile match {
          case _: HsqldbProfile   => Array("db/batch_periodic/migration/hsql", "db/batch_periodic/migration/common")
          case _: PostgresProfile => Array("db/batch_periodic/migration/postgres", "db/batch_periodic/migration/common")
          case _ =>
            throw new IllegalArgumentException(s"Unsupported database url: $url. Use either PostgreSQL or HSQLDB.")
        }): _*
      )
      .dataSource(url, configDb.as[String]("user"), configDb.as[String]("password"))
      .setOptional[String]("schema", _.schemas(_))
      .setOptional[String]("table", _.table(_))
      .baselineOnMigrate(true)
      .load()
      .migrate()

    (JdbcBackend.Database.forConfig(path = "", configDb), profile)
  }

  private def chooseDbProfile(dbUrl: String, schemaName: String): NuJdbcProfile = {
    dbUrl match {
      case url if (new PostgreSQLDatabaseType).handlesJDBCUrl(url) => new NuExPostgresProfile(schemaName)
      case _                                                       => new NuHsqldbProfile(schemaName)
    }
  }

}
