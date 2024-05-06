package pl.touk.nussknacker.ui.process.repository

import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.error.ScenarioNotFoundError
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

// TODO: we should replace FetchingProcessRepository and ProcessRepository by this class after we
//       split things like scenario graph (versions), scenario metadata, state related things (actions)
class ScenarioRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getScenarioMetadata(scenarioName: ProcessName): EitherT[DB, ScenarioNotFoundError, ProcessEntityData] =
    EitherT(
      toEffectAll(
        processesTable
          .filter(_.name === scenarioName)
          .take(1)
          .result
          .headOption
          .map(_.map(Right(_)).getOrElse(Left(ScenarioNotFoundError(scenarioName))))
      )
    )

}
