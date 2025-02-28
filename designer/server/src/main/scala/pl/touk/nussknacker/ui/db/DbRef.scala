package pl.touk.nussknacker.ui.db

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

class DbRef private (val db: JdbcBackend.Database, val profile: ProfileWithDbSchema)

object DbRef {

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
    } yield new DbRef(db, ProfileWithDbSchema(chooseDbProfile(config), schemaName))
  }

  private def chooseDbProfile(config: Config): JdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    config.getAs[String]("db.url") match {
      case Some(jdbcUrlPattern("postgresql")) => PostgresProfile
      case Some(jdbcUrlPattern("hsqldb"))     => HsqldbProfile
      case None                               => HsqldbProfile
      case _                                  => throw new IllegalStateException("unsupported jdbc url")
    }
  }

  private def schemaNameFrom(config: Config) = {
    config.getAs[String]("db.schema").getOrElse("public")
  }

}

final case class ProfileWithDbSchema(jdbcProfile: JdbcProfile, schemaName: String)
