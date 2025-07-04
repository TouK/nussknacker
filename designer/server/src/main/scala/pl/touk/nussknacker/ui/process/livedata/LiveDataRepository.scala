package pl.touk.nussknacker.ui.process.livedata

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata._
import pl.touk.nussknacker.engine.livedata.CollectedLiveData._
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.process.repository.DbioRepository

import scala.concurrent.ExecutionContext

trait LiveDataRepository {

  def cleanLiveData(
      processIdWithName: ProcessIdWithName,
  ): DB[Unit]

  def fetchLiveData(
      processIdWithName: ProcessIdWithName,
      maxNumberOfSamples: Int,
      uploadIntervalInSeconds: Long,
  ): DB[Either[String, CollectedLiveData]]

}

class DbLiveDataRepository(override protected val dbRef: DbRef)(
    implicit executionContext: ExecutionContext,
) extends DbioRepository
    with NuTables
    with LiveDataRepository
    with LazyLogging {

  import dbRef.profile.apiWithEnforcedSchema._

  def cleanLiveData(
      processIdWithName: ProcessIdWithName,
  ): DB[Unit] = {
    flinkLiveDataTable
      .filter(_.scenarioId === processIdWithName.id)
      .delete
      .map(_ => ())
  }

  override def fetchLiveData(
      processIdWithName: ProcessIdWithName,
      maxNumberOfSamples: Int,
      uploadIntervalInSeconds: Long,
  ): DB[Either[String, CollectedLiveData]] = {
    fetchAndAggregateLiveData(processIdWithName, maxNumberOfSamples)
  }

  private def fetchAndAggregateLiveData(
      processIdWithName: ProcessIdWithName,
      maxNumberOfSamples: Int,
  ): DB[Either[String, CollectedLiveData]] = {
    for {
      rawLiveDataFromCollectors <- EitherT.right[String](
        run(
          flinkLiveDataTable
            .filter(_.scenarioId === processIdWithName.id)
            .map(_.liveData)
            .result
            .map(_.flatten)
        )
      )
      parsedLiveDataOrErrorFromCollectors = rawLiveDataFromCollectors.map { liveDataStr =>
        for {
          liveDataJson <- io.circe.parser.parse(liveDataStr).left.map(_.message)
          liveData     <- collectedLiveDataDecoder.decodeJson(liveDataJson).left.map(_.message)
        } yield liveData
      }
      liveDataFromCollectors <- EitherT.fromEither[DB](
        parsedLiveDataOrErrorFromCollectors.toList.reverse.foldLeft(
          Right(Nil): Either[String, List[CollectedLiveData]]
        ) {
          case (Right(acc), Right(result)) => Right(result :: acc)
          case (Left(err), _)              => Left(err)
          case (_, Left(err))              => Left(err)
        }
      )
    } yield CollectedLiveData.aggregate(liveDataFromCollectors, maxNumberOfSamples)
  }.value

}
