package pl.touk.nussknacker.engine.api.component

import cats.Order
import cats.data.NonEmptySet
import io.circe.{Decoder, Encoder}

sealed trait ProcessingMode {
  def value: String
  override def toString: String = value
}

case object RequestResponseProcessingMode extends ProcessingMode {
  override def value: String = "Request-Response"
}

case class StreamProcessingMode(bounded: Boolean) extends ProcessingMode {
  override def value: String = s"${if (bounded) "Bounded" else "Unbounded"}-Stream"
}

object ProcessingMode {

  val RequestResponse: ProcessingMode = RequestResponseProcessingMode
  val UnboundedStream: ProcessingMode = StreamProcessingMode(bounded = false)
  val BoundedStream: ProcessingMode   = StreamProcessingMode(bounded = true)

  val all: Set[ProcessingMode] = Set(UnboundedStream, BoundedStream, RequestResponse)

  implicit val encoder: Encoder[ProcessingMode] = Encoder.encodeString.contramap(_.value)

  implicit val decoder: Decoder[ProcessingMode] = Decoder.decodeString.map {
    case str if str == RequestResponse.value => RequestResponseProcessingMode
    case str if str == UnboundedStream.value => UnboundedStream
    case str if str == BoundedStream.value   => BoundedStream
    case other                               => throw new IllegalArgumentException(s"Not known processing mode: $other")
  }

  sealed trait AllowedProcessingModes

  object AllowedProcessingModes {
    case object AllProcessingModes                                              extends AllowedProcessingModes
    final case class SetOf(allowedProcessingModes: NonEmptySet[ProcessingMode]) extends AllowedProcessingModes
  }

  implicit val processingModeOrdering: Ordering[ProcessingMode] = Ordering.by(_.value)
  implicit val processingModeOrder: Order[ProcessingMode]       = Order.fromOrdering
}
