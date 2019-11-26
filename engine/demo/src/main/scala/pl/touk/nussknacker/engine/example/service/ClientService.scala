package pl.touk.nussknacker.engine.example.service

import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.example.Client
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ClientService extends Service with TimeMeasuringService {

  @MethodToInvoke
  def invoke(@ParamName("clientId") clientId: String)(implicit ec: ExecutionContext): Future[Client] = {
    val clients = Map(
      "Client1" -> Client("Client1", "Alice", "123"),
      "Client2" -> Client("Client2", "Bob", "234"),
      "Client3" -> Client("Client3", "Charles", "345"),
      "Client4" -> Client("Client4", "David", "777"),
      "Client5" -> Client("Client5", "Eve", "888")

    )
    measuring {
      Future {
        Thread.sleep(Random.nextInt(10))
        clients.getOrElse(clientId,
          throw NonTransientException(input = "Client service failure", message = s"Cannot fetch client with id: $clientId")
        )
      }
    }
  }

  override protected def serviceName: String = "clientService"

}
