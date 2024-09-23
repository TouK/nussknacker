package pl.touk.nussknacker.engine.api.component

import cats.syntax.functor._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.syntax._
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

final case class KafkaSourceOffset(offsetResetStrategy: Long) extends NodeDeploymentData

object NodeDeploymentData {

  implicit val nodeDeploymentDataEncoder: Encoder[NodeDeploymentData] =
    Encoder.instance {
      case s: SqlFilteringExpression => s.asJson
      case o: KafkaSourceOffset      => o.asJson
    }

  implicit val nodeDeploymentDataDecoder: Decoder[NodeDeploymentData] =
    List[Decoder[NodeDeploymentData]](
      Decoder[SqlFilteringExpression].widen,
      Decoder[KafkaSourceOffset].widen
    ).reduceLeft(_ or _)

}
