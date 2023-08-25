package pl.touk.nussknacker.ui.db

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

class DbRef private(val db: JdbcBackend.Database, val profile: JdbcProfile)
object DbRef {

  def create(config: Config): Resource[IO, DbRef] = {
    for {
      db <- Resource
        .make(
          acquire = IO(JdbcBackend.Database.forConfig("db", config))
        )(
          release = db => IO(db.close())
        )
      _ <- Resource.eval(IO(DatabaseInitializer.initDatabase("db", config)))
    } yield new DbRef(db, chooseDbProfile(config))
  }

  private def chooseDbProfile(config: Config): JdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    config.getAs[String]("db.url") match {
      case Some(jdbcUrlPattern("postgresql")) => PostgresProfile
      case Some(jdbcUrlPattern("hsqldb")) => HsqldbProfile
      case None => HsqldbProfile
      case _ => throw new IllegalStateException("unsupported jdbc url")
    }
  }
}