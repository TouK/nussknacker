package pl.touk.nussknacker.ui.customhttpservice.services

import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.definition.UIDefinitions
import pl.touk.nussknacker.ui.customhttpservice.services.DefinitionsServiceForHttpService.ExternalComponentUiConfigMode
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

final class NussknackerServicesForCustomHttpService(
    val scenarioService: ScenarioService,
    val dbRef: DbRef,
    val processingTypeServicesProvider: ProcessingTypeServicesProvider
)

trait ProcessingTypeServicesProvider {
  def definitionService: DefinitionsServiceForHttpService
}

trait DefinitionsServiceForHttpService {

  def prepareUIDefinitions(
      processingType: ProcessingType,
      forFragment: Boolean,
      componentUiConfigMode: ExternalComponentUiConfigMode
  )(
      implicit user: LoggedUser
  ): Future[
    UIDefinitions
  ] // todo: this returned type requires custom-http-service-api to depend on nussknacker-restmodel

}

//todo: rethink naming here
object DefinitionsServiceForHttpService {
  sealed trait ExternalComponentUiConfigMode

  object ExternalComponentUiConfigMode {
    case object EnrichedWithUiConfig extends ExternalComponentUiConfigMode

    case object BasicConfig extends ExternalComponentUiConfigMode
  }

}
