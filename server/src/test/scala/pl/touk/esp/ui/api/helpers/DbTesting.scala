package pl.touk.esp.ui.api.helpers

import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.db.migration.SampleDataInserter
import slick.jdbc.JdbcBackend

object DbTesting {
  val db: JdbcBackend.Database = JdbcBackend.Database.forURL(
    url = s"jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
    driver = "org.hsqldb.jdbc.JDBCDriver",
    user = "SA",
    password = ""
  )

  new DatabaseInitializer(db).initDatabase()
  //najlepiej by bylo nie miec tego w testach i kasowac baze po kazdym tescie
  SampleDataInserter.insert(db)
}