package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.ui.db.DbRef.NuJdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}

//TODO: Remove it in next release
trait EnvironmentsEntityFactory {

  protected val profile: NuJdbcProfile
  import profile.apiWithEnforcedSchema._

  val environmentsTable: LTableQuery[EnvironmentsEntityFactory#EnvironmentsEntity] = LTableQuery(
    new EnvironmentsEntity(_)
  )

  class EnvironmentsEntity(tag: Tag) extends TableWithSchema[EnvironmentsEntityData](tag, "environments") {
    def name: Rep[String] = column[String]("name", O.PrimaryKey)

    def * : ProvenShape[EnvironmentsEntityData] = name <> (EnvironmentsEntityData.apply, EnvironmentsEntityData.unapply)
  }

}

final case class EnvironmentsEntityData(name: String)
