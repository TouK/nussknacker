package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.instances.future._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.{NodeAdditionalInfo, PropertiesAdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.validation.AdditionalPropertiesValidator

import scala.concurrent.{ExecutionContext, Future}

/**
  * This class should contain operations invoked for each node (e.g. node validation, retrieving additional data etc.)
  */
class PropertiesResources(val processRepository: FetchingProcessRepository[Future],
                          subprocessRepository: SubprocessRepository,
                          typeToConfig: ProcessingTypeDataProvider[ModelData],
                          additionalPropertyConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]]
                         )(implicit val ec: ExecutionContext)
  extends ProcessDirectives with FailFastCirceSupport with RouteWithUser {

  private val additionalInfoProvider = new AdditionalInfoProvider(typeToConfig)
  private val additionalPropertiesValidator = new AdditionalPropertiesValidator(additionalPropertyConfig)

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    import akka.http.scaladsl.server.Directives._

    pathPrefix("properties" / Segment) { processName =>
      (post & processDetailsForName[Unit](processName)) { process =>
        path("additionalInfo") {
          entity(as[ProcessProperties]) { processProperties =>
            complete {
              additionalInfoProvider.prepareAdditionalInfo(processProperties.toMetaData(process.id), process.processingType)
            }
          }
        } ~ path("validation") {
          val modelData = typeToConfig.forTypeUnsafe(process.processingType)
          implicit val requestDecoder: Decoder[PropertiesValidationRequest] = PropertiesResources.prepareRequestDecoder(modelData)
          entity(as[PropertiesValidationRequest]) { properties =>
            complete {
              val result = additionalPropertiesValidator.validate(DisplayableProcess(process.id, properties.processProperties, List(), List(), process.processingType))

              PropertiesValidationResult(
                parameters = None,
                expressionType = None,
                validationErrors = result.errors.processPropertiesErrors,
                validationPerformed = true)
            }
          }
        }
      }
    }
  }

  object PropertiesResources {

    def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
      new TypingResultDecoder(name => ClassUtils.forName(name, modelData.modelClassLoader.classLoader)).decodeTypingResults
    }

    def prepareRequestDecoder(modelData: ModelData): Decoder[PropertiesValidationRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      deriveConfiguredDecoder[PropertiesValidationRequest]
    }

    def prepareValidationContext(modelData: ModelData)(variableTypes: Map[String, TypingResult])(implicit metaData: MetaData): ValidationContext = {
      val emptyCtx = GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig).emptyValidationContext(metaData)
      //It's a bit tricky, because FE does not distinguish between global and local vars...
      val localVars = variableTypes.filterNot(e => emptyCtx.globalVariables.keys.toSet.contains(e._1))
      emptyCtx.copy(localVariables = localVars)
    }
  }

  class AdditionalInfoProvider(typeToConfig: ProcessingTypeDataProvider[ModelData]) {

    //TODO: do not load provider for each request...
    private val providers: ProcessingTypeDataProvider[Option[MetaData => Future[Option[NodeAdditionalInfo]]]] = typeToConfig.mapValues(pt => ScalaServiceLoader
      .load[PropertiesAdditionalInfoProvider](pt.modelClassLoader.classLoader).headOption.map(_.additionalInfo(pt.processConfig)))

    def prepareAdditionalInfo(metaData: MetaData, processingType: ProcessingType)(implicit ec: ExecutionContext): Future[Option[NodeAdditionalInfo]] = {
      (for {
        provider <- OptionT.fromOption[Future](providers.forType(processingType).flatten)
        data <- OptionT(provider(metaData))
      } yield data).value
    }

  }

  @JsonCodec(encodeOnly = true) case class PropertiesValidationResult(parameters: Option[List[UIParameter]],
                                                                      expressionType: Option[TypingResult],
                                                                      validationErrors: List[NodeValidationError],
                                                                      validationPerformed: Boolean)

  @JsonCodec(encodeOnly = true) case class PropertiesValidationRequest(processProperties: ProcessProperties)
}
