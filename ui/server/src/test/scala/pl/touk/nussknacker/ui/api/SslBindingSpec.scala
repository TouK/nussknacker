package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest._
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.NussknackerApp.system
import pl.touk.nussknacker.ui.security.api.ssl.{HttpsConnectionContextFactory, KeyStoreConfig}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.higherKinds

class SslBindingSpec extends FlatSpec with Matchers {

  it should "connect to api via SSL" in {
    implicit val system: ActorSystem = ActorSystem("SslBindingSpec")
    implicit val materializer: Materializer = ActorMaterializer()

    val route = NussknackerApp.initializeRoute(system.settings.config)
    val keyStoreConfig = KeyStoreConfig(getClass.getResource("/localhost.p12").toURI, "foobar".toCharArray)
    val httpsContext = HttpsConnectionContextFactory.createContext(keyStoreConfig)

    val binding = Await.result(NussknackerApp.bindHttps("localhost", 0, httpsContext, route), 10.seconds) // port = 0 - random port
    try {
      val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")
      val request = HttpRequest(
        uri = Uri("https://localhost:" + binding.localAddress.getPort + "/api/processes"),
        headers = Authorization(credentials) :: Nil)
      val http = Http()
      http.setDefaultClientHttpsContext(httpsContext)
      val response = Await.result(http.singleRequest(request), 10.seconds)
      response.status shouldBe StatusCodes.OK
    } finally {
      Await.result(binding.unbind(), 10.seconds)
      Await.result(system.terminate(), 10.seconds)
    }
  }

}