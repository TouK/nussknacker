package pl.touk.nussknacker.ui.db

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import net.ceedubs.ficus.Ficus._
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

import java.sql.Connection
import scala.util.control.NonFatal

class DbRef private (val db: JdbcBackend.Database, val profile: JdbcProfile)

object DbRef {

  def create(config: Config): Resource[IO, DbRef] = {
    for {
      _  <- Resource.eval(IO(DatabaseInitializer.initDatabase("db", config)))
      ds <- createDataSource(config)
      db <- Resource.make(acquire = IO(JdbcBackend.Database.forDataSource(ds, None)))(
        release = db => IO(db.close())
      )
    } yield new DbRef(db, chooseDbProfile(config))
  }

  private def chooseDbProfile(config: Config): JdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    config.getString("db.url") match {
      case jdbcUrlPattern("postgresql") => PostgresProfile
      case jdbcUrlPattern("hsqldb")     => HsqldbProfile
      case _                            => throw new IllegalStateException("Unsupported JDBC URL")
    }
  }

  private def createDataSource(config: Config): Resource[IO, HikariDataSource] = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.getString("db.url"))
    hikariConfig.setUsername(config.getString("db.user"))
    hikariConfig.setPassword(config.getString("db.password"))
    hikariConfig.setMaximumPoolSize(config.getInt("db.maxPoolSize"))

    Resource.make(IO(createHikariDataSource(hikariConfig)))(ds => IO(ds.close()))
  }

  private def createHikariDataSource(hikariConf: HikariConfig) = {
    new HikariDataSource(hikariConf) {

      override def getConnection: Connection = {
        val conn = super.getConnection
        val stmt = conn.prepareStatement(s"SET SCHEMA '${hikariConf.getSchema}'")
        try {
          stmt.execute()
        } catch {
          case NonFatal(_) =>
            silentClose(conn)
        } finally {
          stmt.close()
        }
        conn
      }

      private def silentClose(conn: Connection): Unit = {
        try {
          conn.close()
        } catch { case NonFatal(_) => }
      }
    }
  }

}
