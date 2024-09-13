package pl.touk.nussknacker.ui.api.description

import cats.data.NonEmptyList
import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{NodeDeploymentData, NodesDeploymentData, SqlFilteringExpression}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  EmptyProcess,
  ExpressionParserCompilationError,
  MissingRequiredProperty
}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, DeploymentStatusName}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{UIGlobalError, ValidationErrors}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.repository.ApiCallComment
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos._

  lazy val runDeploymentEndpoint: SecuredEndpoint[(DeploymentId, RunDeploymentRequest), RunDeploymentError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Run the deployment of a scenario")
      .tag("Deployments")
      .put
      .in(
        "deployments" / deploymentIdPathCapture
      )
      .in(
        jsonBody[RunDeploymentRequest]
          .example(
            RunDeploymentRequest(
              scenarioName = ProcessName("scenario1"),
              NodesDeploymentData(
                Map(NodeId("sourceNodeId1") -> SqlFilteringExpression("field1 = 'value'"))
              ),
              comment = None
            )
          )
      )
      .out(statusCode(StatusCode.Accepted))
      .errorOut(
        oneOf[RunDeploymentError](
          oneOfVariant[ConflictRunDeploymentError](
            StatusCode.Conflict,
            plainBody[ConflictRunDeploymentError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Deployment with id {deploymentId} already exists"),
                    value = ConflictingDeploymentIdError(exampleDeploymentId)
                  )
                )
              )
          ),
          oneOfVariant[BadRequestRunDeploymentError](
            StatusCode.BadRequest,
            plainBody[BadRequestRunDeploymentError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Scenario {scenarioName} not found"),
                    value = ScenarioNotFoundError(ProcessName("'example scenario'"))
                  ),
                  Example.of(
                    summary = Some("Comment is required"),
                    value = CommentValidationError("Comment is required.")
                  ),
                  Example.of(
                    summary = Some("Scenario validation error"),
                    value = ScenarioGraphValidationError(
                      ValidationErrors(
                        invalidNodes = Map(
                          "filter" -> List(
                            PrettyValidationErrors.formatErrorMessage(
                              ExpressionParserCompilationError(
                                message = "Bad expression",
                                paramName = None,
                                originalExpr = "",
                                details = None
                              )(NodeId("filter"))
                            )
                          )
                        ),
                        globalErrors =
                          List(UIGlobalError(PrettyValidationErrors.formatErrorMessage(EmptyProcess), List.empty)),
                        processPropertiesErrors = List(
                          PrettyValidationErrors.formatErrorMessage(
                            MissingRequiredProperty(ParameterName("parallelism"), None)(NodeId("properties"))
                          )
                        ),
                      )
                    )
                  ),
                  Example.of(
                    summary = Some("Deploy validation error"),
                    value = DeployValidationError("Not enough free slots on Flink cluster")
                  )
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val getDeploymentStatusEndpoint
      : SecuredEndpoint[DeploymentId, GetDeploymentStatusError, GetDeploymentStatusResponse, Any] =
    baseNuApiEndpoint
      .summary("Get status of a deployment")
      .tag("Deployments")
      .get
      .in(
        "deployments" / deploymentIdPathCapture / "status"
      )
      .out(
        statusCode(StatusCode.Ok).and(
          jsonBody[GetDeploymentStatusResponse].examples(
            List(
              Example.of(
                GetDeploymentStatusResponse(DeploymentStatus.Running.name, None, exampleInstant),
                Some("RUNNING status")
              ),
              Example.of(
                GetDeploymentStatusResponse(
                  DeploymentStatus.Problem.Failed.name,
                  Some(DeploymentStatus.Problem.Failed.description),
                  exampleInstant
                ),
                Some("PROBLEM status")
              )
            )
          )
        )
      )
      .errorOut(
        oneOf[GetDeploymentStatusError](
          oneOfVariantValueMatcher[DeploymentNotFoundError](
            StatusCode.NotFound,
            plainBody[DeploymentNotFoundError]
              .example(
                Example.of(
                  summary = Some("No deployment {deploymentId} found"),
                  value = DeploymentNotFoundError(exampleDeploymentId)
                )
              )
          ) {
            // MatchType macro used in oneOfVariantFromMatchType doesn't work for this case
            case DeploymentNotFoundError(_) => true
          }
        )
      )
      .withSecurity(auth)

  private lazy val exampleDeploymentId = DeploymentId(UUID.fromString("a9a1e269-0b71-4582-a948-603482d27298"))

  private lazy val exampleInstant = LocalDateTime.of(2024, 1, 1, 0, 0, 0).atZone(ZoneOffset.UTC).toInstant

  private lazy val deploymentIdPathCapture = path[DeploymentId]("deploymentId")
    .copy(info =
      Info
        .empty[DeploymentId]
        .description(
          "Identifier in the UUID format that will be used for the verification of deployment's status"
        )
        .example(exampleDeploymentId)
    )

}

object DeploymentApiEndpoints {

  object Dtos {

    implicit val scenarioNameSchema: Schema[ProcessName] = Schema.string[ProcessName]

    implicit val deploymentIdCodec: PlainCodec[DeploymentId] =
      Codec.uuid.map(DeploymentId(_))(_.value)

    // TODO: scenario graph version / the currently active version instead of the latest
    @derive(encoder, decoder, schema)
    final case class RunDeploymentRequest(
        scenarioName: ProcessName,
        nodesDeploymentData: NodesDeploymentData,
        comment: Option[ApiCallComment]
    )

    implicit val deploymentStatusNameCodec: Schema[DeploymentStatusName] = Schema.string[DeploymentStatusName]

    @derive(encoder, decoder, schema)
    final case class GetDeploymentStatusResponse(
        name: DeploymentStatusName,
        problemDescription: Option[String],
        modifiedAt: Instant
    )

    implicit val nodeDeploymentDataCodec: Schema[NodeDeploymentData] = Schema.derived

    implicit val nodesDeploymentDataCodec: Schema[NodesDeploymentData] = Schema
      .schemaForMap[NodeId, NodeDeploymentData](_.id)
      .map[NodesDeploymentData]((map: Map[NodeId, NodeDeploymentData]) => Some(NodesDeploymentData(map)))(
        _.dataByNodeId
      )

    sealed trait RunDeploymentError

    sealed trait BadRequestRunDeploymentError extends RunDeploymentError

    sealed trait ConflictRunDeploymentError extends RunDeploymentError

    final case class ConflictingDeploymentIdError(id: DeploymentId) extends ConflictRunDeploymentError

    final case class ConcurrentDeploymentsForScenarioArePerformedError(
        scenarioName: ProcessName,
        concurrentDeploymentsIds: NonEmptyList[DeploymentId]
    ) extends ConflictRunDeploymentError

    final case class ScenarioNotFoundError(scenarioName: ProcessName) extends BadRequestRunDeploymentError

    case object DeploymentOfFragmentError extends BadRequestRunDeploymentError

    case object DeploymentOfArchivedScenarioError extends BadRequestRunDeploymentError

    final case class CommentValidationError(message: String) extends BadRequestRunDeploymentError

    final case class ScenarioGraphValidationError(errors: ValidationErrors) extends BadRequestRunDeploymentError

    final case class DeployValidationError(message: String) extends BadRequestRunDeploymentError

    sealed trait GetDeploymentStatusError

    final case class DeploymentNotFoundError(id: DeploymentId) extends GetDeploymentStatusError

    case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError with CustomAuthorizationError

    implicit val badRequestRunDeploymentErrorCodec: Codec[String, BadRequestRunDeploymentError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestRunDeploymentError] {
        case ScenarioNotFoundError(scenarioName)  => s"Scenario $scenarioName not found"
        case DeploymentOfFragmentError            => s"Deployment of fragment is not allowed"
        case DeploymentOfArchivedScenarioError    => s"Deployment of archived scenario is not allowed"
        case CommentValidationError(message)      => message
        case ScenarioGraphValidationError(errors) => toHumanReadableMessage(errors)
        case DeployValidationError(message)       => message
      }

    implicit val conflictingDeploymentIdErrorCodec: Codec[String, ConflictRunDeploymentError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[ConflictRunDeploymentError] {
        case ConflictingDeploymentIdError(id) => s"Deployment with id $id already exists"
        case ConcurrentDeploymentsForScenarioArePerformedError(scenarioName, concurrentDeploymentsIds) =>
          s"Deployment can't be run because only a single deployment per scenario can be run at a time. " +
            s"Currently the scenario [$scenarioName] has running deployments with ids: " +
            s"${concurrentDeploymentsIds.toList.sortBy(_.value).mkString(",")}".stripMargin
      }

    implicit val deploymentNotFoundErrorCodec: Codec[String, DeploymentNotFoundError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[DeploymentNotFoundError](err =>
        s"Deployment ${err.id} not found"
      )

  }

  private def toHumanReadableMessage(errors: ValidationErrors) = {
    // TODO: Move to some details field
    s"Scenario is invalid.${Option(errors.invalidNodes)
        .filterNot(_.isEmpty)
        .map {
          _.map { case (nodeId, nodeErrors) =>
            s"\n  $nodeId: ${nodeErrors.map(_.message).mkString(", ")}"
          }.mkString("\nNode errors:", "", "")
        }
        .getOrElse("")}" +
      s"${Option(errors.globalErrors)
          .filterNot(_.isEmpty)
          .map {
            _.map(_.error.message).mkString("\nGlobal errors: ", ", ", "")
          }
          .getOrElse("")}" +
      s"${Option(errors.processPropertiesErrors)
          .filterNot(_.isEmpty)
          .map {
            _.map(_.message).mkString("\nProperties errors: ", ", ", "")
          }
          .getOrElse("")}"
  }

}
