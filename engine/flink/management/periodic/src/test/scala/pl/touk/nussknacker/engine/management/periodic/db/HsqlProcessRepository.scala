package pl.touk.nussknacker.engine.management.periodic.db

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.Clock
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._

object HsqlProcessRepository {

  def prepare: HsqlProcessRepository = {
    val config = ConfigFactory.empty()
      .withValue("url", fromAnyRef(s"jdbc:hsqldb:mem:periodic-${UUID.randomUUID().toString};sql.syntax_ora=true"))
      .withValue("user", fromAnyRef("SA"))
      .withValue("password", fromAnyRef(""))

    val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(config)
    new HsqlProcessRepository(db, dbProfile)
  }

}

class HsqlProcessRepository(val db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) {

  def forClock(clock: Clock) = new SlickPeriodicProcessesRepository(db, dbProfile, clock)

}


