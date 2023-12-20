package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.Ok
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir._

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.ProcessNameCodec._
  import NodesApiEndpoints._

  lazy val nodesAdditionalInfoEndpoint: SecuredEndpoint[(ProcessName, NodeData), Unit, Option[AdditionalInfo], Any] = {
    implicitly[Schema[NodeData]]
    baseNuApiEndpoint
      .summary("Additional info for provided node")
      .tag("Nodes")
      .post
      .in("nodess" / path[ProcessName]("processName") / "additionalInfo")
      .in(jsonBody[NodeData])
      .out(
        statusCode(Ok).and(
          jsonBody[Option[AdditionalInfo]]
        )
      )
      .withSecurity(auth)
  }

  object NodesApiEndpoints {

    object ProcessNameCodec {
      def encode(processName: ProcessName): String = processName.value

      def decode(s: String): DecodeResult[ProcessName] = {
        val processName = ProcessName.apply(s)
        DecodeResult.Value(processName)
      }

      implicit val processNameCodec: PlainCodec[ProcessName] = Codec.string.mapDecode(decode)(encode)


    }
    implicit val additionalInfoSchema: Schema[AdditionalInfo] = Schema.derived
//    implicit val nodeDataSchema
  }

}
