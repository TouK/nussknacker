package pl.touk.nussknacker.engine.example.service

import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.example.Client

import scala.concurrent.{ExecutionContext, Future}

class ClientService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("clientId") clientId: String)(implicit ec: ExecutionContext): Future[Client] = {
    val clients = Map(
      "ClientA" -> Client("ClientA", "Alice", "123"),
      "ClientB" -> Client("ClientB", "Bob", "234"),
      "ClientC" -> Client("ClientC", "Charles", "345")
    )
    Future {
      clients.getOrElse(clientId,
        throw NonTransientException(input = "Client service failure", message = s"Cannot fetch client with id: $clientId")
      )
    }
  }
}
