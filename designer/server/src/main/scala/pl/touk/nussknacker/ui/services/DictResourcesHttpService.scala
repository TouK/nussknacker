package pl.touk.nussknacker.ui.services

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.dict.DictRegistry.DictNotDeclared
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictQueryService}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.ui.api.DictResourcesEndpoints
import pl.touk.nussknacker.ui.api.DictResourcesEndpoints.DictError
import pl.touk.nussknacker.ui.api.DictResourcesEndpoints.DictError.{MalformedTypingResult, NoDict, NoProcessingType}
import pl.touk.nussknacker.ui.api.DictResourcesEndpoints.Dtos.DictListElementDto
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class DictResourcesHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processingTypeData: ProcessingTypeDataProvider[(DictQueryService, Map[String, DictDefinition], ClassLoader), _]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val dictResourcesEndpoints = new DictResourcesEndpoints(authenticator.authenticationMethod())

  expose {
    dictResourcesEndpoints.dictionaryEntryQueryEndpoint
      .serverSecurityLogic(authorizeKnownUser[DictError])
      .serverLogic { implicit loggedUser: LoggedUser => queryParams =>
        val (processingType, dictId, labelPattern) = queryParams

        processingTypeData.forType(processingType) match {
          case Some((dictQueryService, _, _)) =>
            dictQueryService.queryEntriesByLabel(dictId, labelPattern) match {
              case Valid(dictEntries)          => dictEntries.map(success)
              case Invalid(DictNotDeclared(_)) => Future.successful(businessError(NoDict(dictId)))
            }

          case None => Future.successful(businessError(NoProcessingType(processingType)))
        }
      }
  }

  expose {
    dictResourcesEndpoints.dictionaryListEndpoint
      .serverSecurityLogic(authorizeKnownUser[DictError])
      .serverLogic { implicit loggedUser: LoggedUser => queryParams =>
        val (processingType, dictListRequestDto) = queryParams

        processingTypeData.forType(processingType) match {
          case Some((_, dictionaries, classLoader)) =>
            val decoder = new TypingResultDecoder(ClassUtils.forName(_, classLoader)).decodeTypingResults

            decoder.decodeJson(dictListRequestDto.expectedType.value) match {
              case Left(failure) => Future.successful(businessError(MalformedTypingResult(failure.getMessage())))
              case Right(expectedType) =>
                Future {
                  success(
                    dictionaries
                      .filter { case (id, definition) => definition.valueType(id).canBeSubclassOf(expectedType) }
                      .map { case (id, _) => DictListElementDto(id) }
                      .toList
                  )
                }
            }

          case None => Future.successful(businessError(NoProcessingType(processingType)))
        }
      }
  }

}
