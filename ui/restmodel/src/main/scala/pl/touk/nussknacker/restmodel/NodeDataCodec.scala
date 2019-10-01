package pl.touk.nussknacker.restmodel

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{NodeData, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.NodeAdditionalFields
import shapeless.syntax.typeable._
import io.circe.generic.extras.semiauto._
import pl.touk.nussknacker.engine.api.CirceUtil._

//TODO: make it via annotations after UserDefinedAdditionalNodeFields => NodeAdditionalFields
object NodeDataCodec {

  private implicit val nodeAdditionalFieldsOptEncoder: Encoder[node.UserDefinedAdditionalNodeFields]
    = Encoder[Option[NodeAdditionalFields]].contramap(_.cast[NodeAdditionalFields])

  private implicit val nodeAdditionalFieldsOptDecoder: Decoder[node.UserDefinedAdditionalNodeFields]
    = Decoder[NodeAdditionalFields].map(k => k: UserDefinedAdditionalNodeFields)

  implicit val nodeDataEncoder: Encoder[NodeData] = deriveEncoder

  implicit val nodeDataDecoder: Decoder[NodeData] = deriveDecoder

}