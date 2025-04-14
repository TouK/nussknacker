package pl.touk.nussknacker.ui.api.description.scenarioTests

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe
import io.circe._
import io.circe.derivation.deriveCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.restmodel.definition.UISourceParameters
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{TestSourceParameters, _}
import pl.touk.nussknacker.ui.api.description.scenarioTests.Dtos.Capabilities.TestCapabilityDetails.{
  EmptyDetails,
  TestWithParametersDetails
}
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.Schema
import sttp.tapir.derevo.schema

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
      implicit def codec: Codec[ScenarioTestCapabilities]   = deriveCodec
      implicit def schema: Schema[ScenarioTestCapabilities] = Schema.derived
    }

    sealed trait CapabilityStatus[T <: TestCapabilityDetails]

    object CapabilityStatus {
      final case class NotAvailable[T <: TestCapabilityDetails](reason: NotAvailableReason) extends CapabilityStatus[T]
      final case class Available[T <: TestCapabilityDetails](data: T)                       extends CapabilityStatus[T]
      def available: Available[EmptyDetails] = Available(EmptyDetails())

      implicit def codec[DATA <: TestCapabilityDetails: Codec]: circe.Codec[CapabilityStatus[DATA]] = circe.Codec.from(
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
        implicit def codec: Codec[TestWithParametersDetails]              = deriveCodec
        implicit def uiSourceParametersSchema: Schema[UISourceParameters] = Schema.anyObject
        implicit def schema: Schema[TestWithParametersDetails]            = Schema.derived
      }

      final case class EmptyDetails() extends TestCapabilityDetails

      object EmptyDetails {

        implicit def codec: Codec[EmptyDetails] = Codec.from(
          Decoder.const(EmptyDetails()),
          Encoder.instance(_ => Json.obj())
        )

        implicit def schema: Schema[EmptyDetails] = Schema.derived
      }

    }

    sealed trait NotAvailableReason extends EnumEntry with UpperSnakecase

    object NotAvailableReason extends Enum[NotAvailableReason] with CirceEnum[NotAvailableReason] {
      case object NoSources             extends NotAvailableReason
      case object NotSupportedBySources extends NotAvailableReason
      case object InvalidScenario       extends NotAvailableReason
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

    implicit def codec: circe.Codec[ScenarioTestData] = Codec.from(
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

  implicit def resultContextSchema: Schema[ResultContext[Json]]                           = Schema.derived
  implicit def expressionInvocationResultSchema: Schema[ExpressionInvocationResult[Json]] = Schema.derived
  implicit def externalInvocationResultSchema: Schema[ExternalInvocationResult[Json]]     = Schema.derived
  implicit def throwableSchema: Schema[Throwable]                                         = Schema.string
  implicit def exceptionResultSchema: Schema[ExceptionResult[Json]]                       = Schema.derived
  implicit def edgeOutputSchema: Schema[Map[Edge, List[ResultContext[Json]]]] =
    Schema.schemaForMap[Edge, List[ResultContext[Json]]](edge => edge.sourceNodeId + "->" + edge.destinationNodeId)
  implicit def testResultsSchema: Schema[TestResults[Json]]       = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                 = Schema.anyObject
  implicit def resultsWithCountsSchema: Schema[ResultsWithCounts] = Schema.derived
  implicit def typingResultDecoder: Decoder[TypingResult]         = Decoder.decodeJson.map(_ => typing.Unknown)
  implicit def scenarioGraphSchema: Schema[ScenarioGraph]         = Schema.anyObject

}
