package pl.touk.nussknacker.ui.api.description.scenarioTesting

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe
import io.circe._
import io.circe.derivation.deriveCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TestingApiErrorMessages
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.TestCapabilityDetails.{
  EmptyDetails,
  TestWithParametersDetails
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.api.utils.ValidationErrorOps.ValidationErrorOps
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.{Codec, CodecFormat, Schema}
import sttp.tapir.derevo.schema

import scala.collection.compat._
import scala.collection.immutable

object Dtos {

  import sttp.tapir.json.circe._
  lazy val typingResultEncoder: Encoder[TypingResult] = TypingResult.encoder

  object Capabilities {

    final case class ScenarioTestCapabilities(
        testWithParameters: CapabilityStatus[TestWithParametersDetails],
        testWithGeneratedData: CapabilityStatus[EmptyDetails],
    )

    object ScenarioTestCapabilities {
      implicit def codec: circe.Codec[ScenarioTestCapabilities] = deriveCodec
      implicit def schema: Schema[ScenarioTestCapabilities]     = Schema.derived
    }

    sealed trait CapabilityStatus[T <: TestCapabilityDetails]

    object CapabilityStatus {
      final case class NotAvailable[T <: TestCapabilityDetails](reason: NotAvailableReason) extends CapabilityStatus[T]
      final case class Available[T <: TestCapabilityDetails](data: T)                       extends CapabilityStatus[T]
      def available: Available[EmptyDetails] = Available(EmptyDetails())

      implicit def codec[DATA <: TestCapabilityDetails: circe.Codec]: circe.Codec[CapabilityStatus[DATA]] =
        circe.Codec.from(
          Decoder.instance(c =>
            for {
              statusStr <- c.downField("status").as[String]
              status <- statusStr match {
                case "NOT_AVAILABLE" =>
                  c.downField("reason").as[NotAvailableReason].map(CapabilityStatus.NotAvailable[DATA](_))
                case "AVAILABLE" =>
                  c.as[DATA].map(CapabilityStatus.Available.apply)
              }
            } yield status
          ),
          Encoder.instance {
            case CapabilityStatus.NotAvailable(reason) =>
              Json.obj(
                ("status", "NOT_AVAILABLE".asJson),
                ("reason", reason.asJson),
              )
            case CapabilityStatus.Available(data) =>
              Json
                .obj(
                  ("status", "AVAILABLE".asJson)
                )
                .deepMerge(data.asJson)
                .dropNullValues
          },
        )

      implicit def schema[T <: TestCapabilityDetails: Schema]: Schema[CapabilityStatus[T]] = Schema.derived
    }

    sealed trait TestCapabilityDetails

    object TestCapabilityDetails {
      final case class TestWithParametersDetails(sourceParameters: List[UISourceParameters])
          extends TestCapabilityDetails

      object TestWithParametersDetails {
        implicit def codec: circe.Codec[TestWithParametersDetails]        = deriveCodec
        implicit def uiSourceParametersSchema: Schema[UISourceParameters] = Schema.anyObject
        implicit def schema: Schema[TestWithParametersDetails]            = Schema.derived
      }

      final case class EmptyDetails() extends TestCapabilityDetails

      object EmptyDetails {

        implicit def codec: circe.Codec[EmptyDetails] = circe.Codec.from(
          Decoder.const(EmptyDetails()),
          Encoder.instance(_ => Json.obj())
        )

        implicit def schema: Schema[EmptyDetails] = Schema.derived
      }

    }

    sealed trait NotAvailableReason extends EnumEntry with UpperSnakecase

    object NotAvailableReason extends Enum[NotAvailableReason] with CirceEnum[NotAvailableReason] {
      case object UserDoesNotHavePermission extends NotAvailableReason
      case object NoSources                 extends NotAvailableReason
      case object NotSupportedBySources     extends NotAvailableReason
      case object InvalidScenario           extends NotAvailableReason
      override def values: immutable.IndexedSeq[NotAvailableReason] = findValues
      implicit def schema: Schema[NotAvailableReason]               = Schema.derived
    }

  }

  object Test {

    @derive(schema, encoder, decoder)
    final case class PerformTestRequest(
        scenarioGraph: ScenarioGraph,
        testData: ScenarioTestData,
    )

    final case class SkipResultsPerNode(value: Boolean)

    final case class SkipResultsPerTransition(value: Boolean)

  }

  object Validate {

    @derive(schema, encoder, decoder)
    final case class ScenarioTestValidationRequest(
        scenarioGraph: ScenarioGraph,
        testData: ScenarioTestData,
    )

  }

  object GeneratedTestData {

    @derive(schema, encoder, decoder)
    final case class GeneratedTestDataRequest(
        scenarioGraph: ScenarioGraph,
        numberOfSamples: Int,
    )

  }

  sealed trait ScenarioTestData

  object ScenarioTestData {

    final case class WithParameters(
        sourceParameters: TestSourceParameters,
    ) extends ScenarioTestData

    final case class WithGeneratedData(
        numberOfSamples: Int,
    ) extends ScenarioTestData

    implicit def codec: circe.Codec[ScenarioTestData] = circe.Codec.from(
      Decoder.instance(c =>
        for {
          typeStr <- c.downField("type").as[String]
          testData <- typeStr match {
            case "WITH_PARAMETERS" =>
              c.downField("sourceParameters").as[TestSourceParameters].map(WithParameters.apply)
            case "WITH_GENERATED_DATA" =>
              c.downField("numberOfSamples").as[Int].map(WithGeneratedData.apply)
          }
        } yield testData
      ),
      Encoder.instance {
        case WithParameters(sourceParameters) =>
          Json.obj(
            ("type", "WITH_PARAMETERS".asJson),
            ("sourceParameters", sourceParameters.asJson),
          )
        case WithGeneratedData(numberOfSamples) =>
          Json.obj(
            ("type", "WITH_GENERATED_DATA".asJson),
            ("numberOfSamples", numberOfSamples.asJson),
          )
      }
    )

    implicit def schema: Schema[ScenarioTestData] = Schema.derived

  }

  final case class ResultsWithCountsDto(results: TestResultsDto, counts: Map[String, NodeCount])

  object ResultsWithCountsDto {

    def from(
        resultsWithCounts: ResultsWithCounts,
        skipResultsPerNode: SkipResultsPerNode,
        skipResultsPerTransition: SkipResultsPerTransition
    ): ResultsWithCountsDto = {
      lazy val nodeTransitionResults = resultsWithCounts.results.nodeTransitionResults.map {
        case (nodeTransition, results) =>
          NodeTransitionResult(
            sourceNodeId = nodeTransition.sourceNodeId,
            destinationNodeId = nodeTransition.destinationNodeId,
            results = results,
          )
      }.toList
      lazy val exceptionsByNodeId = resultsWithCounts.results.exceptions.groupBy(_.nodeId).collect {
        case (Some(nodeId), exceptions) => (nodeId, exceptions)
      }
      ResultsWithCountsDto(
        results = TestResultsDto(
          nodeResults = Option.when(!skipResultsPerNode.value)(resultsWithCounts.results.nodeResults),
          nodeTransitionResults = Option.when(!skipResultsPerTransition.value)(nodeTransitionResults),
          invocationResults = resultsWithCounts.results.invocationResults,
          externalInvocationResults = resultsWithCounts.results.externalInvocationResults,
          exceptions = resultsWithCounts.results.exceptions,
          exceptionsByNodeId = exceptionsByNodeId,
        ),
        counts = resultsWithCounts.counts,
      )
    }

  }

  final case class TestResultsDto(
      nodeResults: Option[Map[String, List[ResultContext[Json]]]],
      nodeTransitionResults: Option[List[NodeTransitionResult]],
      invocationResults: Map[String, List[ExpressionInvocationResult[Json]]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult[Json]]],
      exceptions: List[ExceptionResult[Json]],
      exceptionsByNodeId: Map[String, List[ExceptionResult[Json]]],
  )

  final case class NodeTransitionResult(
      sourceNodeId: String,
      destinationNodeId: Option[String],
      results: List[ResultContext[Json]]
  )

  implicit def resultContextSchema: Schema[ResultContext[Json]]                           = Schema.derived
  implicit def expressionInvocationResultSchema: Schema[ExpressionInvocationResult[Json]] = Schema.derived
  implicit def externalInvocationResultSchema: Schema[ExternalInvocationResult[Json]]     = Schema.derived
  implicit def throwableSchema: Schema[Throwable]                                         = Schema.string
  implicit def exceptionResultSchema: Schema[ExceptionResult[Json]]                       = Schema.derived
  implicit def nodeTransitionResultSchema: Schema[NodeTransitionResult]                   = Schema.derived
  implicit def testResultsSchema: Schema[TestResultsDto]                                  = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                                         = Schema.anyObject
  implicit def resultsWithCountsSchema: Schema[ResultsWithCountsDto]                      = Schema.derived
  implicit def typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)
  implicit def scenarioGraphSchema: Schema[ScenarioGraph] = Schema.anyObject

  sealed trait TestingError

  object TestingError {

    final case object NoPermission extends TestingError with CustomAuthorizationError

    sealed trait BadRequestTestingError extends TestingError

    object BadRequestTestingError {
      final case class TooManyCharactersGenerated(length: Int, limit: Int)    extends BadRequestTestingError
      final case class TooManySamplesRequested(maxSamples: Int)               extends BadRequestTestingError
      final case class ScenarioGraphValidationError(errors: ValidationErrors) extends BadRequestTestingError
      final case class UnsupportedOperation(message: String)                  extends BadRequestTestingError
      final case class ErrorResult(message: String)                           extends BadRequestTestingError

      implicit val badRequestTestingErrorCodec: Codec[String, BadRequestTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestTestingError] {
          case ScenarioGraphValidationError(errors) =>
            errors.toHumanReadableMessage
          case TooManyCharactersGenerated(length, limit) =>
            TestingApiErrorMessages.generatedTestData.tooManyCharacters(length, limit)
          case TooManySamplesRequested(maxSamples) =>
            TestingApiErrorMessages.generatedTestData.requestedTooManySamplesToGenerate(maxSamples)
          case UnsupportedOperation(message) =>
            message
          case ErrorResult(message) =>
            message
        }
      }

    }

    sealed trait NotFoundTestingError extends TestingError

    object NotFoundTestingError {
      final case class NoScenario(scenarioName: ProcessName) extends NotFoundTestingError
      final case object NoDataGenerated                      extends NotFoundTestingError
      final case object NoSourcesWithTestDataGeneration      extends NotFoundTestingError

      implicit val notFoundTestingErrorCodec: Codec[String, NotFoundTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NotFoundTestingError] {
          case NoScenario(scenarioName) => s"No scenario ${scenarioName.value} found"
          case NoDataGenerated          => TestingApiErrorMessages.generatedTestData.couldNotProvideTestDataSample
          case NoSourcesWithTestDataGeneration =>
            TestingApiErrorMessages.generatedTestData.noSourcesWithTestDataGeneration
        }
      }

    }

  }

}
