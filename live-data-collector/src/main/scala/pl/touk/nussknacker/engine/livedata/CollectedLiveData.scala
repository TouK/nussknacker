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
    expressionEvaluationResults: Map[NodeId, List[ExpressionEvaluationResult]],
    externalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult]],
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

  private implicit val nodeTransitionCodec: Codec[NodeTransition] =
    Codec.forProduct3("sourceNodeId", "destinationNodeId", "isDirectTransition")(
      (sourceNodeId: NodeId, destinationNodeId: Option[NodeId], isDirectTransition: Option[Boolean]) =>
        NodeTransition(sourceNodeId, destinationNodeId, isDirectTransition.getOrElse(true)),
    )(t => (t.sourceNodeId, t.destinationNodeId, Some(t.isDirectTransition)))

  private implicit val liveDataSampleEncoder: Encoder[LiveDataSample] = deriveEncoder
  private implicit val liveDataSampleDecoder: Decoder[LiveDataSample] = deriveDecoder

  private implicit val liveDataForNodeTransitionEncoder: Encoder[LiveDataForNodeTransition] = deriveEncoder
  private implicit val liveDataForNodeTransitionDecoder: Decoder[LiveDataForNodeTransition] = deriveDecoder

  private implicit val expressionEvaluationResultEncoder: Encoder[ExpressionEvaluationResult] = deriveEncoder
  private implicit val expressionEvaluationResultDecoder: Decoder[ExpressionEvaluationResult] = deriveDecoder

  private implicit val externalServiceInvocationResultEncoder: Encoder[ExternalServiceInvocationResult] = deriveEncoder
  private implicit val externalServiceInvocationResultDecoder: Decoder[ExternalServiceInvocationResult] = deriveDecoder

  private implicit val exceptionResultEncoder: Encoder[ExceptionResult] = deriveEncoder
  private implicit val exceptionResultDecoder: Decoder[ExceptionResult] = deriveDecoder

  implicit val nodeIdKeyEncoder: KeyEncoder[NodeId] = KeyEncoder.encodeKeyString.contramap(_.value)
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

  // The live data collected by standalone Flink are synchronized using `live_data` db table.
  // They are stored in the db as CollectedLiveData encoded as Json.
  // The already deployed processes will upload the data to the table using the old schema after Nu upgrade.
  // This class represents old schema (with `invocationResults` and `externalInvocationResults` fields.
  private final case class CollectedLiveDataV1(
      timestamp: Instant,
      nodeTransitions: Map[NodeTransition, LiveDataForNodeTransition],
      invocationResults: Map[NodeId, List[ExpressionEvaluationResult]],
      externalInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult]],
      exceptions: Map[NodeId, List[ExceptionResult]]
  ) {}

  implicit val collectedLiveDataEncoder: Encoder[CollectedLiveData] = deriveEncoder

  implicit val collectedLiveDataDecoder: Decoder[CollectedLiveData] =
    deriveDecoder[CollectedLiveData]
      .or(deriveDecoder[CollectedLiveDataV1].map { v1 =>
        CollectedLiveData(
          timestamp = v1.timestamp,
          nodeTransitions = v1.nodeTransitions,
          expressionEvaluationResults = v1.invocationResults,
          externalServiceInvocationResults = v1.externalInvocationResults,
          exceptions = v1.exceptions,
        )
      })

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
          expressionEvaluationResults = latestSamples[ExpressionEvaluationResult](
            liveDataNel.toList.map(_.expressionEvaluationResults),
            maxNumberOfSamples,
            _.timestamp
          ),
          externalServiceInvocationResults = latestSamples[ExternalServiceInvocationResult](
            liveDataNel.toList.map(_.externalServiceInvocationResults),
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

final case class ExpressionEvaluationResult(
    contextId: ContextId,
    timestamp: Instant,
    name: String,
    value: Json,
)

final case class ExternalServiceInvocationResult(
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

final case class NodeTransition(
    sourceNodeId: NodeId,
    destinationNodeId: Option[NodeId],
    isDirectTransition: Boolean,
)
