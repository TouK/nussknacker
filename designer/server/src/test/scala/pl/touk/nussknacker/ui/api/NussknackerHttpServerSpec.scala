package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.scalas.CatsTestExtensions._
import pl.touk.nussknacker.test.{DefaultUniquePortProvider, WithTestHttpClientCreator}
import pl.touk.nussknacker.ui.configloader.DesignerConfig
import pl.touk.nussknacker.ui.security.ssl.HttpsConnectionContextFactory.prepareSSLContext
import pl.touk.nussknacker.ui.security.ssl.KeyStoreConfig
import pl.touk.nussknacker.ui.server.{NussknackerHttpServer, RouteProvider}
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode

class NussknackerHttpServerSpec
    extends AnyFlatSpec
    with Matchers
    with WithTestHttpClientCreator
    with DefaultUniquePortProvider {

  it should "allow client to connect via SSL" in {
    eval {
      val keyStoreConfig = KeyStoreConfig(getClass.getResource("/localhost.p12").toURI, "foobar".toCharArray)
      val port           = nextPort()
      for {
        server <- createHttpServer()
        client <- createHttpClient(Some(prepareSSLContext(keyStoreConfig)))
        _ <- server.start(
          DesignerConfig.from(
            ConfigFactory
              .empty()
              .withValue("http.interface", fromAnyRef("0.0.0.0"))
              .withValue("http.port", fromAnyRef(port))
              .withValue("ssl.enabled", fromAnyRef("true"))
              .withValue("ssl.keyStore.location", fromAnyRef(keyStoreConfig.uri.getPath))
              .withValue("ssl.keyStore.password", fromAnyRef("foobar"))
          ),
          new MetricRegistry
        )
      } yield {
        val response = client.send(basicRequest.get(uri"https://localhost:$port/test"))
        response.code should be(StatusCode.Ok)
      }
    }
  }

  private def createHttpServer() = {
    Resource
      .make(
        acquire = IO(ActorSystem("SslBindingSpec", ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine))
      )(
        release = system => IO(system.terminate())
      )
      .map { system =>
        new NussknackerHttpServer(DummyRouteProvider, system)
      }
  }

  private object DummyRouteProvider extends RouteProvider[Route] with Directives {

    override def createRoute(config: DesignerConfig): Resource[IO, Route] = Resource.pure[IO, Route] {
      path("test") {
        get {
          complete {
            "ok"
          }
        }
      }
    }

  }

}
