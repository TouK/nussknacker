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
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class DictApiHttpService(
    authManager: AuthManager,
    processingTypeData: ProcessingTypeDataProvider[(DictQueryService, Map[String, DictDefinition], ClassLoader), _]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val dictApiEndpoints = new DictApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    dictApiEndpoints.dictionaryEntryQueryEndpoint
      .serverSecurityLogic(authorizeKnownUser[DictError])
      .serverLogic { implicit loggedUser: LoggedUser => queryParams =>
        val (processingType, dictId, labelPattern) = queryParams

        processingTypeData.forProcessingType(processingType) match {
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
    dictApiEndpoints.dictionaryListEndpoint
      .serverSecurityLogic(authorizeKnownUser[DictError])
      .serverLogic { implicit loggedUser: LoggedUser => queryParams =>
        val (processingType, dictListRequestDto) = queryParams

        processingTypeData.forProcessingType(processingType) match {
          case Some((_, dictionaries, classLoader)) =>
            val decoder = new TypingResultDecoder(ClassUtils.forName(_, classLoader)).decodeTypingResults

            decoder.decodeJson(dictListRequestDto.expectedType) match {
              case Left(failure) => Future.successful(businessError(MalformedTypingResult(failure.getMessage())))
              case Right(expectedType) =>
                Future {
                  success(
                    dictionaries
                      .filter { case (id, definition) =>
                        definition.valueType(id).canBeStrictlyConvertedTo(expectedType)
                      }
                      .map { case (id, _) => DictDto(id, id) }
                      .toList
                  )
                }
            }

          case None => Future.successful(businessError(NoProcessingType(processingType)))
        }
      }
  }

}
