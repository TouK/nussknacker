package pl.touk.nussknacker.test

import cats.effect.{ContextShift, IO, Resource}
import org.scalatest.{BeforeAndAfterAll, Suite}
import sttp.client3.{HttpClientSyncBackend, Identity, SttpBackend}
import sttp.client3.logging.slf4j._

import java.net.http.HttpClient
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext

trait WithTestHttpClientCreator {

  def createHttpClient(sslContext: Option[SSLContext] = None): Resource[IO, SttpBackend[Identity, Any]] = {
    implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    Resource
      .make(
        acquire = IO {
          val httpClient = sslContext match {
            case Some(ssl) => HttpClient.newBuilder().sslContext(ssl).build()
            case None => HttpClient.newBuilder().build()
          }
          Slf4jLoggingBackend(HttpClientSyncBackend.usingClient(httpClient))
        }
      )(
        release = client => IO(client.close())
      )
  }

}
object WithTestHttpClientCreator extends WithTestHttpClientCreator

trait WithTestHttpClient extends BeforeAndAfterAll {
  this: Suite =>

  private val (client, clientResources) = WithTestHttpClientCreator
    .createHttpClient(sslContext = None)
    .allocated
    .unsafeRunSync()

  def httpClient: SttpBackend[Identity, Any] = client

  abstract override protected def afterAll(): Unit = {
    clientResources.unsafeRunSync()
    super.afterAll()
  }
}
