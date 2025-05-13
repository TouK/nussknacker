package pl.touk.nussknacker.test

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}
import sttp.client3.{HttpClientSyncBackend, Identity, SttpBackend}
import sttp.client3.logging.LoggingBackend
import sttp.client3.logging.slf4j._

import java.net.http.HttpClient
import javax.net.ssl.SSLContext

trait WithTestHttpClientCreator extends WithSttpTestUtils {

  def createHttpClient(sslContext: Option[SSLContext] = None): Resource[IO, SttpBackend[Identity, Any]] = {
    Resource
      .make(
        acquire = IO {
          val httpClient = sslContext match {
            case Some(ssl) => HttpClient.newBuilder().sslContext(ssl).build()
            case None      => HttpClient.newBuilder().build()
          }
          val backend = HttpClientSyncBackend.usingClient(httpClient)
          LoggingBackend(
            delegate = backend,
            logger = new Slf4jLogger("nu-test", backend.responseMonad),
            logRequestBody = true,
            logResponseBody = true
          )
        }
      )(
        release = client => IO(client.close())
      )
  }

}

object WithTestHttpClientCreator extends WithTestHttpClientCreator

trait WithTestHttpClient extends WithSttpTestUtils with BeforeAndAfterAll {
  this: Suite =>

  private val (client, clientResources) = WithTestHttpClientCreator
    .createHttpClient(sslContext = None)
    .allocated
    .unsafeRunSync()

  def httpClient: SttpBackend[Identity, Any] = client

  override protected def afterAll(): Unit = {
    clientResources.unsafeRunSync()
    super.afterAll()
  }

}
