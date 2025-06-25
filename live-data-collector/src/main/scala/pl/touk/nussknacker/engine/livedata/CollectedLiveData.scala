package pl.touk.nussknacker.engine.livedata

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.{ContextId, ContextIdPathPart, NodeId}

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
          scenarioName: String,
          nodeId: String,
          taskId: Long,
          index: Long,
          path: List[ContextIdPathPart]
      ) => ContextId(scenarioName, nodeId, taskId, index, path),
    )(cid => (cid.scenarioName, cid.originatingNodeId, cid.taskId, cid.index, cid.path))

  private implicit val throwableEncoder: Encoder[Throwable] = Encoder.encodeString.contramap(_.getMessage)
  private implicit val throwableDecoder: Decoder[Throwable] = Decoder.decodeString.map(new RuntimeException(_))

  private implicit val instantEncoder: Encoder[Instant] = Encoder.encodeLong.contramap(_.getEpochSecond)
  private implicit val instantDecoder: Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochSecond)

  private implicit val nodeIdEncoder: Encoder[NodeId] = Encoder.encodeString.contramap(_.id)
  private implicit val nodeIdDecoder: Decoder[NodeId] = Decoder.decodeString.map(NodeId(_))

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

final case class NodeTransition(sourceNodeId: String, destinationNodeId: Option[String])
