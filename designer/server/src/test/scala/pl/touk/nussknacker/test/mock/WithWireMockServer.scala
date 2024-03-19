package pl.touk.nussknacker.test.mock

import com.github.tomakehurst.wiremock.WireMockServer
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.AvailablePortFinder

trait WithWireMockServer extends BeforeAndAfterAll { self: Suite =>

  private val wireMockServer: WireMockServer = {
    val server = AvailablePortFinder.withAvailablePortsBlocked(1)(l => new WireMockServer(l.head))
    server.start()
    setupWireMockServer(server)
    server
  }

  protected def setupWireMockServer(wireMockServer: WireMockServer): Unit

  protected def wireMockServerBaseUrl: String = wireMockServer.baseUrl()

  override protected def afterAll(): Unit = {
    try {
      wireMockServer.stop()
    } finally {
      super.afterAll()
    }
  }

}
