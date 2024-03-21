package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.dict.DictRegistry.DictNotDeclared
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictQueryService}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.DictError
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.DictError.{
  MalformedTypingResult,
  NoDict,
  NoProcessingType
}
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.Dtos.DictDto
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class DictApiHttpService(
    authenticator: AuthenticationResources,
    processingTypeData: ProcessingTypeDataProvider[(DictQueryService, Map[String, DictDefinition], ClassLoader), _]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val dictResourcesEndpoints = new DictApiEndpoints(authenticator.authenticationMethod())

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
                      .map { case (id, _) => DictDto(id) }
                      .toList
                  )
                }
            }

          case None => Future.successful(businessError(NoProcessingType(processingType)))
        }
      }
  }

}
