package pl.touk.esp.engine.example.service

import pl.touk.esp.engine.api.exception.NonTransientException
import pl.touk.esp.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.esp.engine.example.Client

import scala.concurrent.{ExecutionContext, Future}

class ClientService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("clientId") clientId: String)(implicit ec: ExecutionContext): Future[Client] = {
    val clients = Map(
      "1" -> Client("1", "Alice", "123"),
      "2" -> Client("1", "Bob", "234")
    )
    Future {
      clients.getOrElse(clientId,
        throw NonTransientException(input = "Client service failure", message = s"Cannot fetch client with id: $clientId")
      )
    }
  }
}
