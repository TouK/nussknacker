package pl.touk.nussknacker.engine.management.periodic.db

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import slick.jdbc
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits._

object HsqlProcessRepository {

  def prepare(clock: Clock): (SlickPeriodicProcessesRepository, JdbcBackend.DatabaseDef) = {
    val config = ConfigFactory.empty()
      .withValue("url", fromAnyRef("jdbc:hsqldb:mem:periodic;sql.syntax_ora=true"))
      .withValue("user", fromAnyRef(""))
      .withValue("password", fromAnyRef(""))

    val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(config)
    (new SlickPeriodicProcessesRepository(db, dbProfile, clock), db)
  }

}
