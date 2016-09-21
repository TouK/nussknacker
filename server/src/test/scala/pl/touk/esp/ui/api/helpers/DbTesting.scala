package pl.touk.esp.ui.api.helpers

import pl.touk.esp.ui.db.DatabaseInitializer
import slick.jdbc.JdbcBackend

object DbTesting {
  val db: JdbcBackend.Database = JdbcBackend.Database.forURL(
    url = s"jdbc:hsqldb:mem:esp",
    driver = "org.hsqldb.jdbc.JDBCDriver",
    user = "SA",
    password = ""
  )

  new DatabaseInitializer(db).initDatabase()

}