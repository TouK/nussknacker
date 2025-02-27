package pl.touk.nussknacker.engine.management.sample.service

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.{DisplayJsonWithEncoder, MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{
  CollectableAction,
  ServiceInvocationCollector,
  TransmissionNames
}
import pl.touk.nussknacker.engine.management.sample.dto.Client

import scala.concurrent.{ExecutionContext, Future}

class ClientFakeHttpService() extends Service {

  @JsonCodec case class LogClientRequest(method: String, id: String) extends DisplayJsonWithEncoder[LogClientRequest]
  @JsonCodec case class LogClientResponse(body: String)              extends DisplayJsonWithEncoder[LogClientResponse]

  @MethodToInvoke
  def invoke(
      @ParamName("id") id: String
  )(implicit executionContext: ExecutionContext, collector: ServiceInvocationCollector): Future[Client] = {
    val req = LogClientRequest("GET", id)
    collector.collectWithResponse(req, None)(
      {
        val client = Client(id, "foo")
        Future.successful(CollectableAction(() => LogClientResponse(client.asJson.spaces2), client))
      },
      TransmissionNames("request", "response")
    )
  }

}
