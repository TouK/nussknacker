package pl.touk.nussknacker.ui.db

import cats.effect.{IO, Resource}
import com.github.tminglei.slickpg.ExPostgresProfile
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.ui.db.DbRef.NuJdbcProfile
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc._

class DbRef private (val db: JdbcBackend.Database, val profile: NuJdbcProfile)

object DbRef {

  type NuJdbcProfile = NuProfile

  def create(config: Config): Resource[IO, DbRef] = {
    for {
      schemaName <- Resource.eval(IO(schemaNameFrom(config)))
      _ <- Resource.eval(IO {
        SlickMigration.setConfiguredAppSchema(schemaName)
        DatabaseInitializer.initDatabase("db", config, schemaName)
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

trait NuProfile extends JdbcProfile {
  this: JdbcProfile =>

  def schemaName: String

  val apiWithEnforcedSchema: ApiWithEnforcedSchema = new ApiWithEnforcedSchema {}

  trait ApiWithEnforcedSchema extends super.API {
    abstract class TableWithSchema[T](tag: Tag, tableName: String)
        extends super.Table[T](tag, Some(schemaName), tableName)
  }

}

class NuPostgresProfile(override val schemaName: String)   extends PostgresProfile with NuProfile
class NuExPostgresProfile(override val schemaName: String) extends NuPostgresProfile(schemaName) with ExPostgresProfile
class NuHsqldbProfile(override val schemaName: String)     extends PostgresProfile with NuProfile
