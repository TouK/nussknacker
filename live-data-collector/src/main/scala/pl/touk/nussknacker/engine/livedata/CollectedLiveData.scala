package pl.touk.nussknacker.engine.livedata

import cats.data.NonEmptyList
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.{ContextId, ContextIdPathPart, NodeId}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}

import java.time.Instant

final case class CollectedLiveData(
    timestamp: Instant,
    nodeTransitions: Map[NodeTransition, LiveDataForNodeTransition],
    invocationResults: Map[NodeId, List[InvocationResult]],
    externalInvocationResults: Map[NodeId, List[InvocationResult]],
    exceptions: Map[NodeId, List[ExceptionResult]]
)

object CollectedLiveData {

  def empty: CollectedLiveData = CollectedLiveData(Instant.now, Map.empty, Map.empty, Map.empty, Map.empty)

  private implicit def contextIdPathPartCodec: Codec[ContextIdPathPart] =
    Codec.forProduct2("n", "v")(ContextIdPathPart.apply)(t => (t.nodeId, t.value))

  private implicit def contextIdCodec: Codec[ContextId] =
    Codec.forProduct5("sn", "nid", "tid", "idx", "path")(
      (
          scenarioName: ProcessName,
          nodeId: NodeId,
          taskId: Long,
          index: Long,
          path: List[ContextIdPathPart]
      ) => ContextId(scenarioName, nodeId, taskId, index, path),
    )(cid => (cid.scenarioName, cid.originatingNodeId, cid.taskId, cid.index, cid.path))

  private implicit val throwableEncoder: Encoder[Throwable] = Encoder.encodeString.contramap(_.getMessage)
  private implicit val throwableDecoder: Decoder[Throwable] = Decoder.decodeString.map(new RuntimeException(_))

  private implicit val instantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.getEpochSecond)
  private implicit val instantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochSecond)

  private implicit val nodeTransitionEncoder: Encoder[NodeTransition] = deriveEncoder
  private implicit val nodeTransitionDecoder: Decoder[NodeTransition] = deriveDecoder

  private implicit val liveDataSampleEncoder: Encoder[LiveDataSample] = deriveEncoder
  private implicit val liveDataSampleDecoder: Decoder[LiveDataSample] = deriveDecoder

  private implicit val liveDataForNodeTransitionEncoder: Encoder[LiveDataForNodeTransition] = deriveEncoder
  private implicit val liveDataForNodeTransitionDecoder: Decoder[LiveDataForNodeTransition] = deriveDecoder

  private implicit val invocationResultEncoder: Encoder[InvocationResult] = deriveEncoder
  private implicit val invocationResultDecoder: Decoder[InvocationResult] = deriveDecoder

  private implicit val exceptionResultEncoder: Encoder[ExceptionResult] = deriveEncoder
  private implicit val exceptionResultDecoder: Decoder[ExceptionResult] = deriveDecoder

  implicit val nodeIdKeyEncoder: KeyEncoder[NodeId] = KeyEncoder.encodeKeyString.contramap(_.id)
  implicit val nodeIdKeyDecoder: KeyDecoder[NodeId] = KeyDecoder.decodeKeyString.map(NodeId.apply)

  implicit val nodeTransitionKeyEncoder: KeyEncoder[NodeTransition] =
    KeyEncoder.encodeKeyString.contramap(nt => nt.asJson.noSpaces)

  implicit val nodeTransitionKeyDecoder: KeyDecoder[NodeTransition] =
    KeyDecoder.decodeKeyString.map { str =>
      val result = for {
        json           <- io.circe.parser.parse(str).left.map(_.message)
        nodeTransition <- json.as[NodeTransition].left.map(_.message)
      } yield nodeTransition
      result.getOrElse(throw new IllegalArgumentException("Could not parse NodeTransition"))
    }

  implicit val collectedLiveDataEncoder: Encoder[CollectedLiveData] = deriveEncoder
  implicit val collectedLiveDataDecoder: Decoder[CollectedLiveData] = deriveDecoder

  def aggregate(
      liveData: List[CollectedLiveData],
      maxNumberOfSamples: Int,
  ): CollectedLiveData = {
    NonEmptyList.fromList(liveData) match {
      case Some(liveDataNel) =>
        CollectedLiveData(
          // We assume that the aggregated liveData has the timestamp of the latest of the aggregated ones
          timestamp = liveDataNel.toList.map(_.timestamp).max,
          // For each of the following CollectedLiveData fields we use `maxNumberOfSamples` latest samples
          nodeTransitions = latestSamples(
            liveDataNel.toList.map(_.nodeTransitions),
            maxNumberOfSamples
          ),
          invocationResults = latestSamples[InvocationResult](
            liveDataNel.toList.map(_.invocationResults),
            maxNumberOfSamples,
            _.timestamp
          ),
          externalInvocationResults = latestSamples[InvocationResult](
            liveDataNel.toList.map(_.externalInvocationResults),
            maxNumberOfSamples,
            _.timestamp
          ),
          exceptions = latestSamples[ExceptionResult](
            liveDataNel.toList.map(_.exceptions),
            maxNumberOfSamples,
            _.timestamp
          ),
        )
      case None => CollectedLiveData.empty
    }
  }

  private def latestSamples(
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

  private def latestSamples[V](
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

final case class ExceptionResult(
    contextId: ContextId,
    timestamp: Instant,
    variables: Map[String, Json],
    throwable: Throwable,
)

final case class InvocationResult(
    contextId: ContextId,
    timestamp: Instant,
    name: String,
    value: Json,
)

final case class LiveDataForNodeTransition(
    samples: List[LiveDataSample],
    totalCount: Long,
    currentThroughput: BigDecimal,
)

case class LiveDataSample(
    contextId: ContextId,
    timestamp: Instant,
    variables: Map[String, Json],
)

final case class NodeTransition(sourceNodeId: NodeId, destinationNodeId: Option[NodeId])
