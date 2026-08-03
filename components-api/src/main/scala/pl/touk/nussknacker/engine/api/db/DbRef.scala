package pl.touk.nussknacker.engine.api.db

import com.github.tminglei.slickpg.ExPostgresProfile
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile}

final case class DbRef(db: JdbcBackend.Database, profile: NuJdbcProfile)

trait NuJdbcProfile extends JdbcProfile {
  this: JdbcProfile =>

  def schemaName: String

  val apiWithEnforcedSchema: ApiWithEnforcedSchema = new ApiWithEnforcedSchema {}

  trait ApiWithEnforcedSchema extends super.JdbcAPI {
    abstract class TableWithSchema[T](tag: Tag, tableName: String) extends Table[T](tag, Some(schemaName), tableName)
  }

}

class NuPostgresProfile(override val schemaName: String) extends ExPostgresProfile with NuJdbcProfile
class NuHsqldbProfile(override val schemaName: String)   extends HsqldbProfile with NuJdbcProfile
