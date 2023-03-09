package pl.touk.nussknacker.ui.db

import slick.jdbc.{JdbcBackend, JdbcProfile}

case class DbConfig(db: JdbcBackend.Database, profile: JdbcProfile)