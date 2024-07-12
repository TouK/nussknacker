package pl.touk.nussknacker.engine.api.component

import cats.Order
import enumeratum._
import io.circe.{Decoder, Encoder}

import scala.collection.immutable

sealed trait ProcessingMode extends EnumEntry

object ProcessingMode extends Enum[ProcessingMode] {
  case object RequestResponse extends ProcessingMode
  case object BoundedStream   extends ProcessingMode
  case object UnboundedStream extends ProcessingMode

  override def values: immutable.IndexedSeq[ProcessingMode] = findValues

  private val RequestResponseJsonValue = "Request-Response"
  private val BoundedStreamJsonValue   = "Bounded-Stream"
  private val UnboundedStreamJsonValue = "Unbounded-Stream"

  implicit class ProcessingModeOps(processingMode: ProcessingMode) {

    def toJsonString: String = processingMode match {
      case ProcessingMode.RequestResponse => RequestResponseJsonValue
      case ProcessingMode.BoundedStream   => BoundedStreamJsonValue
      case ProcessingMode.UnboundedStream => UnboundedStreamJsonValue
    }

  }

  implicit val processingModeEncoder: Encoder[ProcessingMode] = Encoder.encodeString.contramap(_.toJsonString)

  implicit val processingModeDecoder: Decoder[ProcessingMode] = Decoder.decodeString.map {
    case RequestResponseJsonValue => ProcessingMode.RequestResponse
    case BoundedStreamJsonValue   => ProcessingMode.BoundedStream
    case UnboundedStreamJsonValue => ProcessingMode.UnboundedStream
    case other                    => throw new IllegalArgumentException(s"Unknown processing mode: $other")
  }

  implicit val processingModeOrdering: Ordering[ProcessingMode] = Ordering.by(_.toJsonString)
  implicit val processingModeOrder: Order[ProcessingMode]       = Order.fromOrdering
}
