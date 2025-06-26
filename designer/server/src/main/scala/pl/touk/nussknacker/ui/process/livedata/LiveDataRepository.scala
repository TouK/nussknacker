package pl.touk.nussknacker.ui.process.livedata

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata._
import pl.touk.nussknacker.engine.livedata.CollectedLiveData._
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}
import pl.touk.nussknacker.ui.customhttpservice.services.DbRef
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.process.repository.DbioRepository

import java.time.Instant
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
            collectedLiveData.toList.map(_.nodeTransitions),
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

  private def aggregate(
      data: List[Map[NodeTransition, LiveDataForNodeTransition]],
      maxNumberOfSamples: Int,
  ): Map[NodeTransition, LiveDataForNodeTransition] = {
    data.flatten.toGroupedMap
      .mapValuesNow { entries =>
        LiveDataForNodeTransition(
          samples = entries
            .flatMap(_.samples)
            .sortBy(_.timestamp)
            .takeRight(maxNumberOfSamples),
          totalCount = entries.map(_.totalCount).sum,
          currentThroughput = entries.map(_.currentThroughput).sum,
        )

      }
  }

  private def aggregate[V](
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

}
