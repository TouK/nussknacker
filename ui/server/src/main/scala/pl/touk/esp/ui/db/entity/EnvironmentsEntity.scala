package pl.touk.esp.ui.db.entity

import db.migration.DefaultJdbcProfile.profile.api._

object EnvironmentsEntity {

  class EnvironmentsEntity(tag: Tag) extends Table[EnvironmentsEntityData](tag, "environments") {

    def name = column[String]("name", O.PrimaryKey)

    def * = name <> (EnvironmentsEntityData.apply, EnvironmentsEntityData.unapply)

  }

  case class EnvironmentsEntityData(name: String)

}

