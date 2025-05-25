package pl.touk.nussknacker.ui.process.test

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataCodecs
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder.CollectedLiveData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.repository.DbioRepository

import scala.concurrent.ExecutionContext

trait LiveDataRepository {

  def fetchLiveData(
      processIdWithName: ProcessIdWithName,
  ): DB[Either[String, CollectedLiveData]]

}

class DbLiveDataRepository(override protected val dbRef: DbRef)(
    implicit executionContext: ExecutionContext,
) extends DbioRepository
    with NuTables
    with LiveDataRepository
    with LazyLogging {

  import dbRef.profile.apiWithEnforcedSchema._

  override def fetchLiveData(
      processIdWithName: ProcessIdWithName,
  ): DB[Either[String, CollectedLiveData]] = {
    run(
      flinkLiveDataTable
        .filter(_.scenarioId === processIdWithName.id)
        .map(_.liveData)
        .result
        .headOption
        .map(_.flatten)
    ).map { liveDataStrOpt =>
      for {
        liveDataStr  <- liveDataStrOpt.toRight("Empty live data")
        liveDataJson <- io.circe.parser.parse(liveDataStr).left.map(_.message)
        liveData     <- LiveDataCodecs.collectedLiveDataDecoder.decodeJson(liveDataJson).left.map(_.message)
      } yield liveData
    }
  }

}
