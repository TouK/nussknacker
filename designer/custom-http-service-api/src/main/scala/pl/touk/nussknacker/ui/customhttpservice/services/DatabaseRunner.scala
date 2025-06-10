package pl.touk.nussknacker.ui.customhttpservice.services

import com.github.tminglei.slickpg.ExPostgresProfile
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

import scala.concurrent.Future

trait DatabaseRunner {
  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  def runInTransaction[T](action: DB[T]): Future[T]
  def run[T](action: DB[T]): Future[T]
}

case class DbRefInstance(db: JdbcBackend.Database, profile: NuJdbcProfile)

trait NuJdbcProfile extends JdbcProfile {
  this: JdbcProfile =>

  def schemaName: String

  val apiWithEnforcedSchema: ApiWithEnforcedSchema = new ApiWithEnforcedSchema {}

  trait ApiWithEnforcedSchema extends super.API {
    abstract class TableWithSchema[T](tag: Tag, tableName: String)
        extends super.Table[T](tag, Some(schemaName), tableName)
  }

}

class NuPostgresProfile(override val schemaName: String)   extends PostgresProfile with NuJdbcProfile
class NuExPostgresProfile(override val schemaName: String) extends NuPostgresProfile(schemaName) with ExPostgresProfile
class NuHsqldbProfile(override val schemaName: String)     extends HsqldbProfile with NuJdbcProfile
