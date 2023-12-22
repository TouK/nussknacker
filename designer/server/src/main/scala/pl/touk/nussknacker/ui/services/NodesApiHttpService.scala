package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Json}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.ui.additionalInfo.AdditionalInfoProviders
import pl.touk.nussknacker.ui.api.{NodeValidationRequest, NodesApiEndpoints, NodesResources}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessService}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.validation.NodeValidator

import scala.concurrent.{ExecutionContext, Future}

class NodesApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    getProcessCategoryService: () => ProcessCategoryService,
    typeToConfig: ProcessingTypeDataProvider[ModelData, _],
    typeToNodeValidator: ProcessingTypeDataProvider[NodeValidator, _],
    protected val processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val nodesApiEndpoints = new NodesApiEndpoints(authenticator.authenticationMethod())

  private val additionalInfoProviders = new AdditionalInfoProviders(typeToConfig)

  expose {
    nodesApiEndpoints.nodesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user => pair =>
        val (processName, nodeData) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                additionalInfoProviders
                  .prepareAdditionalInfoForNode(nodeData, process.processingType)(executionContext, user)
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
      }
  }

  expose {
    nodesApiEndpoints.nodesValidationEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user => pair =>
        val (processName, nodeValidationRequest) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                //                here need to add decoding from string json to NodeValidationRequest

              }
          }

      }
  }

  expose {
    nodesApiEndpoints.propertiesAdditionalInfoEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic { user => pair =>
        val (processName, processProperties) = pair

        processService
          .getProcessId(processName)
          .flatMap { processId =>
            processService
              .getProcessWithDetails(
                ProcessIdWithName(processId, processName),
                GetScenarioWithDetailsOptions.detailsOnly
              )(user)
              .flatMap { process =>
                additionalInfoProviders
                  .prepareAdditionalInfoForProperties(
                    processProperties.toMetaData(processName),
                    process.processingType
                  )(executionContext, user)
                  .map { additionalInfo =>
                    success(additionalInfo)
                  }
              }
          }
      }
  }

}
