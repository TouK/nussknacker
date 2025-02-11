package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData

final case class NodesDeploymentData(dataByNodeId: Map[NodeId, NodeDeploymentData])

object NodesDeploymentData {

  // Raw deployment parameters (name -> value) that are used as additional node configuration during deployment.
  // Each node can be provided with dedicated set of parameters.
  // TODO: consider replacing NodeDeploymentData with Json
  type NodeDeploymentData = Map[String, String]

  val empty: NodesDeploymentData = NodesDeploymentData(Map.empty)

  implicit val nodesDeploymentDataEncoder: Encoder[NodesDeploymentData] = Encoder
    .encodeMap[NodeId, NodeDeploymentData]
    .contramap(_.dataByNodeId)

  implicit val nodesDeploymentDataDecoder: Decoder[NodesDeploymentData] =
    Decoder.decodeMap[NodeId, NodeDeploymentData].map(NodesDeploymentData(_))

}
