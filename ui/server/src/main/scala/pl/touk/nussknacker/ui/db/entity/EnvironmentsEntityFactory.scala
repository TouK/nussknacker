package pl.touk.nussknacker.ui.db.entity

import slick.jdbc.JdbcProfile
import slick.lifted.{TableQuery => LTableQuery}

trait EnvironmentsEntityFactory {
  protected val profile: JdbcProfile

  import profile.api._
  
  class EnvironmentsEntity(tag: Tag) extends Table[EnvironmentsEntityData](tag, "environments") {

    def name = column[String]("name", O.PrimaryKey)

    def * = name <> (EnvironmentsEntityData.apply, EnvironmentsEntityData.unapply)

  }
  
  val environmentsTable: LTableQuery[EnvironmentsEntityFactory#EnvironmentsEntity] = LTableQuery(new EnvironmentsEntity(_))
  
}

case class EnvironmentsEntityData(name: String)