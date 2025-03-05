package pl.touk.nussknacker.ui.customhttpservice

import com.typesafe.config.Config
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.WithTestHttpClient
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{ConfigWithScalaVersion, WithDesignerConfig}
import sttp.client3.{UriContext, quickRequest}
import sttp.model.StatusCode

class CustomHttpServiceTest
    extends AnyFunSuiteLike
    with NuItTest
    with WithDesignerConfig
    with WithTestHttpClient
    with Matchers
    with OptionValues {

  override def designerConfig: Config = ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine

  test("send request to the http endpoint exposed by CustomHttpService SPI with authentication data") {
    val response1 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.body shouldEqual "testResponse"
  }

  test("send request to the http endpoint exposed by second CustomHttpService SPI with authentication data") {
    val response1 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/custom/secondTestProvider/testPathPart")
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.body shouldEqual "testResponse"
  }

  test("send request to the http endpoint exposed by CustomHttpService SPI without authentication data") {
    val response1 = httpClient.send(
      quickRequest.get(uri"$nuDesignerHttpAddress/api/custom/testProvider/testPathPart")
    )
    response1.code shouldEqual StatusCode.Unauthorized
    response1.body shouldEqual "The resource requires authentication, which was not supplied with the request"
  }

}
