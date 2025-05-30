package pl.touk.nussknacker.engine.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId

import java.time.Instant

final case class CollectedLiveData(
    timestamp: Instant,
    nodeTransitions: Map[NodeTransition, LiveDataForNodeTransition],
    invocationResults: Map[NodeId, List[InvocationResult]],
    externalInvocationResults: Map[NodeId, List[InvocationResult]],
    exceptions: Map[NodeId, List[ExceptionResult]]
)

final case class ExceptionResult(
    contextId: String,
    timestamp: Instant,
    variables: Map[String, Json],
    throwable: Throwable,
)

final case class InvocationResult(
    contextId: String,
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
    contextId: String,
    timestamp: Instant,
    variables: Map[String, Json],
)

final case class NodeTransition(sourceNodeId: String, destinationNodeId: Option[String])
