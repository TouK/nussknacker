package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.NodeId

final case class NodesDeploymentData(dataByNodeId: Map[NodeId, NodeDeploymentData])

object NodesDeploymentData {

  val empty: NodesDeploymentData = NodesDeploymentData(Map.empty)

  implicit val nodesDeploymentDataEncoder: Encoder[NodesDeploymentData] = Encoder
    .encodeMap[NodeId, NodeDeploymentData]
    .contramap(_.dataByNodeId)

  implicit val nodesDeploymentDataDecoder: Decoder[NodesDeploymentData] =
    Decoder.decodeMap[NodeId, NodeDeploymentData].map(NodesDeploymentData(_))

}

sealed trait NodeDeploymentData

final case class SqlFilteringExpression(sqlExpression: String) extends NodeDeploymentData

object NodeDeploymentData {

  implicit val nodeDeploymentDataEncoder: Encoder[NodeDeploymentData] =
    deriveUnwrappedEncoder[SqlFilteringExpression].contramap { case sqlExpression: SqlFilteringExpression =>
      sqlExpression
    }

  implicit val nodeDeploymentDataDecoder: Decoder[NodeDeploymentData] =
    deriveUnwrappedDecoder[SqlFilteringExpression].map(identity)

}
