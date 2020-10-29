package pl.touk.nussknacker.ui.security.http

import java.util.concurrent.atomic.AtomicReference

import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RecordingSttpBackend(delegate: SttpBackend[Future, Nothing, Nothing]) extends SttpBackend[Future, Nothing, Nothing] {

  type RequestAndResponse = (Request[_, _], Try[Response[_]])

  private val _allInteractions = new AtomicReference[Vector[RequestAndResponse]](Vector())

  private def addInteraction(request: Request[_, _], response: Try[Response[_]]): Unit = {
    _allInteractions.updateAndGet((t: Vector[RequestAndResponse]) => t.:+((request, response)))
  }

  override def send[T](request: Request[T, Nothing]): Future[Response[T]] = {
    implicit val m: MonadError[Future] = responseMonad

    responseMonad.map(responseMonad.handleError(delegate.send(request)) {
        case e: Exception =>
          addInteraction(request, Failure(e))
          responseMonad.error(e)
      }) { response =>
        addInteraction(request, Success(response))
        response
      }
  }

  override def openWebsocket[T, WS_RESULT](request: Request[T, Nothing], handler: Nothing): Future[WebSocketResponse[WS_RESULT]] = ???

  override def close(): Future[Unit] = delegate.close()
  override def responseMonad: MonadError[Future] = delegate.responseMonad

  def allInteractions: List[RequestAndResponse] = _allInteractions.get().toList

  def clear(): Any = _allInteractions.set(Vector())

}
