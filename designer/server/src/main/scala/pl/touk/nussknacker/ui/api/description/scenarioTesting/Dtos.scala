package pl.touk.nussknacker.ui.api.description.scenarioTesting

import cats.data.NonEmptyList
import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe
import io.circe._
import io.circe.derivation.deriveCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCase}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.restmodel.validation.testcase.{AssertionIndex, AssertionValidationError}
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TestingApiErrorMessages
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.TestCapabilityDetails.{
  EmptyDetails,
  TestWithParametersDetails
}
import pl.touk.nussknacker.ui.api.utils.ValidationErrorOps.ValidationErrorOps
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}
import sttp.tapir.derevo.schema

import scala.collection.compat._
import scala.collection.immutable

object Dtos {

  lazy val typingResultEncoder: Encoder[TypingResult] = TypingResult.encoder

  object Capabilities {

    final case class ScenarioTestCapabilities(
        testWithParameters: CapabilityStatus[TestWithParametersDetails],
        // TODO: legacy: to be removed
        testWithGeneratedData: CapabilityStatus[EmptyDetails],
        testWithLiveData: CapabilityStatus[EmptyDetails],
    )

    object ScenarioTestCapabilities {
      implicit def codec: circe.Codec[ScenarioTestCapabilities] = deriveCodec
      implicit def schema: Schema[ScenarioTestCapabilities]     = Schema.derived
    }

    sealed trait CapabilityStatus[+T <: TestCapabilityDetails]

    object CapabilityStatus {
      final case class NotAvailable(reason: NotAvailableReason)       extends CapabilityStatus[Nothing]
      final case class Available[T <: TestCapabilityDetails](data: T) extends CapabilityStatus[T]
      def available: Available[EmptyDetails] = Available(EmptyDetails())

      implicit def codec[DATA <: TestCapabilityDetails: circe.Codec]: circe.Codec[CapabilityStatus[DATA]] =
        circe.Codec.from(
          Decoder.instance(c =>
            for {
              statusStr <- c.downField("status").as[String]
              status <- statusStr match {
                case "NOT_AVAILABLE" =>
                  c.downField("reason").as[NotAvailableReason].map(CapabilityStatus.NotAvailable)
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

    sealed trait PerformTestRequest

    @derive(schema, encoder, decoder)
    final case class PerformTestRequestJsonBody(
        scenarioGraph: ScenarioGraph,
        testData: ScenarioTestData,
    ) extends PerformTestRequest

    @derive(schema)
    final case class PerformTestRequestMultiParts(
        scenarioGraph: ScenarioGraph,
        testData: String,
    ) extends PerformTestRequest

    object PerformTestRequestMultiParts {
      implicit val scenarioGraphMultipartPartCodec: Codec[String, ScenarioGraph, CodecFormat.Json] =
        sttp.tapir.json.circe.circeCodec
    }

    @derive(schema, encoder, decoder)
    final case class PerformTestCaseRequest(
        scenarioGraph: ScenarioGraph,
        testCase: TestCase,
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

  object LiveDataFetching {

    @derive(schema, encoder, decoder)
    final case class FetchSourcesLiveDataRequest(
        scenarioGraph: ScenarioGraph,
        numberOfSamples: Int,
    )

  }

  sealed trait ScenarioTestData

  object ScenarioTestData {

    // TODO: legacy: to be removed
    private val legacyLiveDataType = "WITH_GENERATED_DATA"

    final case class WithParameters(
        sourceParameters: TestSourceParameters,
    ) extends ScenarioTestData

    final case class WithLiveData(
        numberOfSamples: Int,
    ) extends ScenarioTestData

    implicit def codec: circe.Codec[ScenarioTestData] = circe.Codec.from(
      Decoder.instance(c =>
        for {
          typeStr <- c.downField("type").as[String]
          testData <- typeStr match {
            case "WITH_PARAMETERS" =>
              c.downField("sourceParameters").as[TestSourceParameters].map(WithParameters.apply)
            case `legacyLiveDataType` | "WITH_LIVE_DATA" =>
              c.downField("numberOfSamples").as[Int].map(WithLiveData.apply)
          }
        } yield testData
      ),
      Encoder.instance {
        case WithParameters(sourceParameters) =>
          Json.obj(
            ("type", "WITH_PARAMETERS".asJson),
            ("sourceParameters", sourceParameters.asJson),
          )
        case WithLiveData(numberOfSamples) =>
          Json.obj(
            ("type", "WITH_LIVE_DATA".asJson),
            ("numberOfSamples", numberOfSamples.asJson),
          )
      }
    )

    implicit def schema: Schema[ScenarioTestData] = Schema.derived

  }

  implicit def typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)
  implicit def scenarioGraphSchema: Schema[ScenarioGraph] = Schema.anyObject
  implicit def testCaseSchema: Schema[TestCase]           = Schema.derived
  implicit def enricherMocksSchema: Schema[Map[NodeId, EnricherMock]] =
    Schema.schemaForMap[NodeId, EnricherMock](_.toString)
  implicit def assertionsSchema: Schema[Map[NodeId, List[Assertion]]] =
    Schema.schemaForMap[NodeId, List[Assertion]](_.toString)

  sealed trait TestingError

  object TestingError {

    final case object NoPermission extends TestingError with CustomAuthorizationError

    sealed trait BadRequestTestingError extends TestingError

    object BadRequestTestingError {
      final case class TooManyCharactersGenerated(length: Int, limit: Int) extends BadRequestTestingError
      final case class TooManyRecordsRequested(maxRecordsCount: Int)       extends BadRequestTestingError

      final case class SourcesCompilationError(
          errors: ValidationErrors,
          nodeNamesById: Map[NodeId, String]
      ) extends BadRequestTestingError

      final case class TestingWithCustomInputNotSupportedError(nodeId: NodeId) extends BadRequestTestingError
      final case class ErrorResult(message: String)                            extends BadRequestTestingError

      implicit val badRequestTestingErrorCodec: Codec[String, BadRequestTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestTestingError] {
          case SourcesCompilationError(errors, nodeNamesById) =>
            errors.toHumanReadableMessage(nodeNamesById)
          case TooManyCharactersGenerated(length, limit) =>
            TestingApiErrorMessages.liveDataFetching.tooManyCharacters(length, limit)
          case TooManyRecordsRequested(maxRecordsCount) =>
            TestingApiErrorMessages.liveDataFetching.requestedTooManyRecordsToFetch(maxRecordsCount)
          case TestingWithCustomInputNotSupportedError(sourceId) =>
            TestingApiErrorMessages.testingWithCustomInput.notSupportedBySource(sourceId)
          case ErrorResult(message) =>
            message
        }
      }

    }

    sealed trait NotFoundTestingError extends TestingError

    object NotFoundTestingError {
      final case class NoScenario(scenarioName: ProcessName) extends NotFoundTestingError
      final case object NoLiveDataAvailable                  extends NotFoundTestingError
      final case object NoSourcesWithLiveDataFetchingSupport extends NotFoundTestingError

      implicit val notFoundTestingErrorCodec: Codec[String, NotFoundTestingError, CodecFormat.TextPlain] = {
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NotFoundTestingError] {
          case NoScenario(scenarioName) => s"No scenario ${scenarioName.value} found"
          case NoLiveDataAvailable      => TestingApiErrorMessages.liveDataFetching.noLiveDataAvailable
          case NoSourcesWithLiveDataFetchingSupport =>
            TestingApiErrorMessages.liveDataFetching.noSourcesWithLiveDataFetching
        }
      }

    }

  }

}
