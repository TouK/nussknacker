package pl.touk.nussknacker.ui.process.livedata

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata._
import pl.touk.nussknacker.engine.livedata.CollectedLiveData._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.repository.DbioRepository

import java.time.Instant
import scala.concurrent.ExecutionContext

trait LiveDataRepository {

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

  override def fetchLiveData(
      processIdWithName: ProcessIdWithName,
      maxNumberOfSamples: Int,
      uploadIntervalInSeconds: Long,
  ): DB[Either[String, CollectedLiveData]] = {
    for {
      _        <- removeOldEntries(processIdWithName, uploadIntervalInSeconds)
      liveData <- fetchAndAggregateLiveData(processIdWithName, maxNumberOfSamples)
    } yield liveData
  }

  private def fetchAndAggregateLiveData(
      processIdWithName: ProcessIdWithName,
      maxNumberOfSamples: Int,
  ) = {
    run(
      flinkLiveDataTable
        .filter(_.scenarioId === processIdWithName.id)
        .map(_.liveData)
        .result
        .map(_.flatten)
    ).map(_.map { liveDataStr =>
      for {
        liveDataJson <- io.circe.parser.parse(liveDataStr).left.map(_.message)
        liveData     <- collectedLiveDataDecoder.decodeJson(liveDataJson).left.map(_.message)
      } yield liveData
    }).map(
      _.reverse.foldLeft(Right(Nil): Either[String, List[CollectedLiveData]]) {
        case (Right(acc), Right(result)) => Right(result :: acc)
        case (Left(err), _)              => Left(err)
        case (_, Left(err))              => Left(err)
      }
    ).map(_.map {
      aggregate(_, maxNumberOfSamples)
    })
  }

  private def aggregate(
      collectedLiveData: List[CollectedLiveData],
      maxNumberOfSamples: Int,
  ): CollectedLiveData = {
    NonEmptyList.fromList(collectedLiveData) match {
      case Some(collectedLiveData) =>
        CollectedLiveData(
          timestamp = collectedLiveData.toList.map(_.timestamp).min,
          nodeTransitions = aggregate(
            collectedLiveData.toList.map(_.nodeTransitions).toList,
            maxNumberOfSamples
          ),
          invocationResults = aggregate[InvocationResult](
            collectedLiveData.toList.map(_.invocationResults),
            maxNumberOfSamples,
            _.timestamp
          ),
          externalInvocationResults = aggregate[InvocationResult](
            collectedLiveData.toList.map(_.externalInvocationResults),
            maxNumberOfSamples,
            _.timestamp
          ),
          exceptions = aggregate[ExceptionResult](
            collectedLiveData.toList.map(_.exceptions),
            maxNumberOfSamples,
            _.timestamp
          ),
        )
      case None => CollectedLiveData.empty
    }
  }

  def aggregate(
      data: List[Map[NodeTransition, LiveDataForNodeTransition]],
      maxNumberOfSamples: Int,
  ): Map[NodeTransition, LiveDataForNodeTransition] = {
    data.flatten
      .groupBy(_._1)
      .mapValuesNow { entries =>
        LiveDataForNodeTransition(
          samples = entries
            .flatMap(_._2.samples)
            .sortBy(_.timestamp)
            .takeRight(maxNumberOfSamples),
          totalCount = entries.map(_._2.totalCount).sum,
          currentThroughput = entries.map(_._2.currentThroughput).sum,
        )

      }
  }

  def aggregate[V](
      data: List[Map[NodeId, List[V]]],
      maxNumberOfSamples: Int,
      getTimestamp: V => Instant
  ): Map[NodeId, List[V]] = {
    data.flatten
      .groupBy(_._1)
      .mapValuesNow { entries =>
        val allValues = entries.flatMap(_._2)
        allValues
          .sortBy(getTimestamp)
          .takeRight(maxNumberOfSamples)
      }
  }

  private def removeOldEntries(
      processIdWithName: ProcessIdWithName,
      uploadIntervalInSeconds: Long,
  ) = {
    run(
      flinkLiveDataTable
        .filter(_.scenarioId === processIdWithName.id)
        .filter(_.updatedAt < Instant.now.getEpochSecond - uploadIntervalInSeconds - 10)
        .delete
    )
  }

}
