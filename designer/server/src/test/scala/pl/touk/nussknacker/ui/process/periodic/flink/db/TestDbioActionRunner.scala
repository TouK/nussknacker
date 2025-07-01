package pl.touk.nussknacker.ui.process.periodic.flink.db

import pl.touk.nussknacker.engine.api.db.{DbRef, NuPostgresProfile}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

object TestDbioActionRunner
    extends DBIOActionRunner(
      DbRef(
        Database.forURL(
          url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
          driver = "org.h2.Driver"
        ),
        new NuPostgresProfile("test")
      )
    )(ExecutionContext.global)
