package pl.touk.nussknacker.ui.db

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.ui.customhttpservice.services.{DbRef, NuHsqldbProfile, NuJdbcProfile, NuPostgresProfile}
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc._

object DbRefInstance {

  val defaultSchemaName = "public"

  def create(config: Config): Resource[IO, DbRef] = {
    for {
      schemaName <- Resource.eval(IO(schemaNameFrom(config)))
      _ <- Resource.eval(IO {
        SlickMigration.setConfiguredAppSchema(schemaName)
        DatabaseInitializer.initDatabase("db", config)
      })
      db <- Resource
        .make(
          acquire = IO(JdbcBackend.Database.forConfig("db", config))
        )(
          release = db => IO(db.close())
        )
    } yield new DbRef(db, chooseDbProfile(config))
  }

  private def chooseDbProfile(config: Config): NuJdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    val schema         = schemaNameFrom(config)
    config.getAs[String]("db.url") match {
      case Some(jdbcUrlPattern("postgresql"))    => new NuPostgresProfile(schema)
      case Some(jdbcUrlPattern("hsqldb")) | None => new NuHsqldbProfile(schema)
      case _                                     => throw new IllegalStateException("unsupported jdbc url")
    }
  }

  private def schemaNameFrom(config: Config) = {
    config.getAs[String]("db.schema").getOrElse("public")
  }

}
