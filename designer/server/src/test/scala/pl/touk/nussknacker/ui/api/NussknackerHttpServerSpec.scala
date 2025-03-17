package pl.touk.nussknacker.ui.api

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.dropwizard.metrics5.MetricRegistry
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{DefaultUniquePortProvider, WithTestHttpClientCreator}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.utils.scalas.CatsTestExtensions._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.security.ssl.HttpsConnectionContextFactory.prepareSSLContext
import pl.touk.nussknacker.ui.security.ssl.KeyStoreConfig
import pl.touk.nussknacker.ui.server.NussknackerHttpServer
import sttp.client3.{basicRequest, UriContext}
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
        server <- createHttpServer(port, keyStoreConfig)
        client <- createHttpClient(Some(prepareSSLContext(keyStoreConfig)))
        route = createRoute()
        _ <- server.start(route)
      } yield {
        val response = client.send(basicRequest.get(uri"https://localhost:$port/test"))
        response.code should be(StatusCode.Ok)
      }
    }
  }

  private def createHttpServer(port: Int, keyStoreConfig: KeyStoreConfig) = {
    Resource
      .make(
        acquire = IO(ActorSystem("SslBindingSpec", ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine))
      )(
        release = system => IO(system.terminate())
      )
      .map { system =>
        val designerConfig = DesignerConfig.from(
          ConfigFactory
            .empty()
            .withValue("http.interface", fromAnyRef("0.0.0.0"))
            .withValue("http.port", fromAnyRef(port))
            .withValue("ssl.enabled", fromAnyRef("true"))
            .withValue("ssl.keyStore.location", fromAnyRef(keyStoreConfig.uri.getPath))
            .withValue("ssl.keyStore.password", fromAnyRef("foobar"))
        )
        new NussknackerHttpServer(system, new MetricRegistry, designerConfig)
      }
  }

  private def createRoute(): Route = {
    path("test") {
      get {
        complete {
          "ok"
        }
      }
    }
  }

}
