package pl.touk.esp.ui.db.migration
import pl.touk.esp.ui.db.migration.CreateEnvironmentsMigration.EnvironmentsEntityData

trait CreateEnvironmentsMigration extends SlickMigration {
  import profile.api._

  override def migrateActions = {
    environmentsTable.schema.create andThen createSample
  }

  private def createSample = {
    environmentsTable += EnvironmentsEntityData("test")
  }

  val environmentsTable = TableQuery[EnvironmentsEntity]
  class EnvironmentsEntity(tag: Tag) extends Table[EnvironmentsEntityData](tag, "environments") {

    def name = column[String]("name", O.PrimaryKey)

    def * = name <> (EnvironmentsEntityData.apply, EnvironmentsEntityData.unapply)

  }
}

object CreateEnvironmentsMigration {

  case class EnvironmentsEntityData(name: String)

}