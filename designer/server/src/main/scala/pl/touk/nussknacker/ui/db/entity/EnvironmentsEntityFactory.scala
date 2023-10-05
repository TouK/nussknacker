package pl.touk.nussknacker.ui.db.entity

import slick.jdbc.JdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}

//TODO: Remove it in next release
trait EnvironmentsEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._

  val environmentsTable: LTableQuery[EnvironmentsEntityFactory#EnvironmentsEntity] = LTableQuery(
    new EnvironmentsEntity(_)
  )

  class EnvironmentsEntity(tag: Tag) extends Table[EnvironmentsEntityData](tag, "environments") {
    def name: Rep[String] = column[String]("name", O.PrimaryKey)

    def * : ProvenShape[EnvironmentsEntityData] = name <> (EnvironmentsEntityData.apply, EnvironmentsEntityData.unapply)
  }

}

final case class EnvironmentsEntityData(name: String)
