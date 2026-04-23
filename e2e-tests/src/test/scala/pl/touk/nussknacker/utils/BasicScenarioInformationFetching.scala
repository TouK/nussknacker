package pl.touk.nussknacker.utils

import io.circe.generic.JsonCodec
import io.circe.parser
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.utils.BasicScenarioInformationFetching.ScenarioBasicInformation

trait BasicScenarioInformationFetching extends WithE2EInstallationExampleRestAssuredUsersExtensions {

  protected val designerServiceUrl: String

  protected def fetchScenariosBasicInformation(): List[ScenarioBasicInformation] = {
    val responseBodyString =
      given()
        .when()
        .request()
        .basicAuthAdmin()
        .get(s"$designerServiceUrl/api/processes")
        .Then()
        .statusCode(200)
        .extract()
        .body()
        .asString()

    parser
      .parse(responseBodyString)
      .toTry
      .flatMap(_.as[List[ScenarioBasicInformation]].toTry)
      .getOrElse(throw new Exception(s"Could not decode scenario basic information from $responseBodyString"))
  }

}

object BasicScenarioInformationFetching {

  @JsonCodec
  final case class ScenarioBasicInformation(
      name: String,
      isArchived: Boolean,
      isFragment: Boolean,
      processingType: String,
      processCategory: String,
      labels: List[String],
      state: ScenarioState
  )

  @JsonCodec
  final case class ScenarioState(status: ScenarioStatus)

  @JsonCodec
  final case class ScenarioStatus(name: String)

}
