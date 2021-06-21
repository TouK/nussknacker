package pl.touk.nussknacker.ui.db

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.jdbc.DriverDataSource.DriverType

object DatabaseInitializer {

  def initDatabase(path: String, config: Config): Unit = {
    import net.ceedubs.ficus.Ficus._
    val configDb = config.getConfig(path)
    DatabaseInitializer.initDatabase(
      configDb.as[String]("url"),
      configDb.as[String]("user"),
      configDb.as[String]("password"),
      configDb.getAs[String]("schema"))
  }

  def initDatabase(url: String, user: String, password: String, schema: Option[String] = None): Unit =
    Flyway
      .configure()
      .locations(
        (url match {
          case hsqldbUrl if DriverType.HSQL.matches(hsqldbUrl) => Array("db/migration/hsql", "db/migration/common")
          case postgresqlUrl if DriverType.POSTGRESQL.matches(postgresqlUrl) => Array("db/migration/postgres", "db/migration/common")
          case _ => throw new IllegalArgumentException(s"Unsuported database url: $url . Use either PostgreSQL or HSQLDB.")
        }): _*
      )
      .dataSource(url, user, password)
      .schemas(schema.toArray: _*)
      .baselineOnMigrate(true)
      .load().migrate()
}
