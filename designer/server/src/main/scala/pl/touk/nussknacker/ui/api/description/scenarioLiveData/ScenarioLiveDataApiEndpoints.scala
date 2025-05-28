package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.Dtos.LiveDataError._
import sttp.model.StatusCode
import sttp.model.StatusCode._
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody

class ScenarioLiveDataApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import Dtos._
  import LiveDataCodecs._

  def scenarioLiveDataEndpoint: SecuredEndpoint[
    ProcessName,
    LiveDataError,
    LiveDataDto,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Preview of the data samples currently processed by the scenario")
      .tag("Live data")
      .get
      .in("liveData" / path[ProcessName]("scenarioName"))
      .out(statusCode(Ok).and(jsonBody[LiveDataDto]))
      .errorOut(errorOutput)
      .withSecurity(auth)

  private val errorOutput = oneOf[LiveDataError](
    oneOfVariantFromMatchType[NoScenario.type](
      NotFound,
      plainBody[NoScenario.type]
        .examples(
          List(
            Example.of(
              summary = Some("Scenario not found"),
              value = NoScenario
            ),
          )
        )
    ),
    oneOfVariant[LiveDataNotSupported.type](
      NotImplemented,
      plainBody[LiveDataNotSupported.type]
        .examples(
          List(
            Example.of(
              summary = Some("Live data preview is not supported by this scenario"),
              value = LiveDataNotSupported
            ),
          )
        )
    ),
    oneOfVariant[LiveDataNotAvailable.type](
      StatusCode.NoContent,
      emptyOutputAs(LiveDataNotAvailable).description("There is currently no live data available for this scenario")
    )
  )

}
