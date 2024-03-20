package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.test.WithTestHttpClient
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.config.{ConfigWithScalaVersion, WithDesignerConfig}
import pl.touk.nussknacker.ui.api.DictResourcesEndpoints.Dtos.{DictListRequestDto, TypingResultInJson}
import sttp.client3.{UriContext, quickRequest}
import sttp.model.{MediaType, StatusCode}

class DictsResourcesTest
    extends AnyFunSuiteLike
    with NuItTest
    with WithDesignerConfig
    with WithTestHttpClient
    with Matchers {

  override def designerConfig: Config = ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine

  test("return list of available dictionaries for DictParameterEditor - String") {
    val response = httpClient.send(
      quickRequest
        .post(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts"
        )
        .contentType(MediaType.ApplicationJson)
        .body(
          DictListRequestDto(TypingResultInJson(Typed[String].asJson)).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )

    response.code shouldEqual StatusCode.Ok
    response.bodyAsJson shouldEqual Json.arr(
      Map("dictId" -> "rgb").asJson,
      Map("dictId" -> "bc").asJson,
      Map("dictId" -> "dict").asJson
    )
  }

  test("return list of available dictionaries for DictParameterEditor - Long") {
    val response = httpClient.send(
      quickRequest
        .post(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts"
        )
        .contentType(MediaType.ApplicationJson)
        .body(
          DictListRequestDto(TypingResultInJson(Typed[Long].asJson)).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )

    response.code shouldEqual StatusCode.Ok
    response.bodyAsJson shouldEqual Json.arr(
      Map("dictId" -> "long_dict").asJson
    )

  }

  test("fail for bad request") {
    val response = httpClient.send(
      quickRequest
        .post(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts"
        )
        .contentType(MediaType.ApplicationJson)
        .body(
          DictListRequestDto(TypingResultInJson(Json.fromString("qwerty"))).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )

    response.code shouldEqual StatusCode.BadRequest
    response.body should include("The request content was malformed")
  }

  test("fail to return dict list for non-existing processingType") {
    val response = httpClient.send(
      quickRequest
        .post(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/thisProcessingTypeDoesNotExist/dicts"
        )
        .contentType(MediaType.ApplicationJson)
        .body(
          DictListRequestDto(TypingResultInJson(Typed[Long].asJson)).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )

    response.code shouldEqual StatusCode.NotFound
    response.body shouldEqual s"Processing type: thisProcessingTypeDoesNotExist not found"
  }

  test("return suggestions for existing prefix") {
    val dictId = "rgb"

    val response1 = httpClient.send(
      quickRequest
        .get(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts/$dictId/entry?label=${"Black"
              .take(2)}"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.bodyAsJson shouldEqual Json.arr(
      Json.obj(
        "key"   -> Json.fromString("H000000"),
        "label" -> Json.fromString("Black")
      ),
      Json.obj(
        "key"   -> Json.fromString("H0000ff"),
        "label" -> Json.fromString("Blue")
      )
    )
  }

  test("return 0 suggestions for non-existing prefix") {
    val dictId = "rgb"

    val response1 = httpClient.send(
      quickRequest
        .get(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts/$dictId/entry?label=thisPrefixDoesNotExist"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.bodyAsJson shouldEqual Json.arr()
  }

  test("fail to return entry suggestions for non-existing dictionary") {
    val dictId = "thisDictDoesNotExist"

    val response1 = httpClient.send(
      quickRequest
        .get(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts/$dictId/entry?label=a"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.NotFound
    response1.body shouldEqual s"Dictionary with id: $dictId not found"
  }

  test("fail to return entry suggestions for non-existing processingType") {
    val dictId         = "rgb"
    val processingType = "thisProcessingTypeDoesNotExist"

    val response1 = httpClient.send(
      quickRequest
        .get(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/$processingType/dicts/$dictId/entry?label=a"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.NotFound
    response1.body shouldEqual s"Processing type: $processingType not found"
  }

}
