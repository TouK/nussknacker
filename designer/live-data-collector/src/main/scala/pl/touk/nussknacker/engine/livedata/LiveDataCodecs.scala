package pl.touk.nussknacker.engine.livedata

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.generic.semiauto._
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder.CollectedLiveData
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.time.Instant
import scala.util.Try

object LiveDataCodecs {

  implicit val instantEncoder: Encoder[Instant] = Encoder.encodeString.contramap(_.toString)

  implicit val instantDecoder: Decoder[Instant] = Decoder.decodeString.emap { str =>
    Try(Instant.parse(str)).toEither.left.map(_.getMessage)
  }

  implicit val throwableEncoder: Encoder[Throwable] =
    Encoder.encodeString.contramap(_.toString)

  implicit val throwableDecoder: Decoder[Throwable] =
    Decoder.decodeString.map(new RuntimeException(_))

  implicit val nodeTransitionEncoder: Encoder[NodeTransition] = deriveEncoder
  implicit val nodeTransitionDecoder: Decoder[NodeTransition] = deriveDecoder

  implicit def expressionInvocationResultEncoder[T: Encoder]: Encoder[ExpressionInvocationResult[T]] = deriveEncoder
  implicit def expressionInvocationResultDecoder[T: Decoder]: Decoder[ExpressionInvocationResult[T]] = deriveDecoder

  implicit def externalInvocationResultEncoder[T: Encoder]: Encoder[ExternalInvocationResult[T]] = deriveEncoder
  implicit def externalInvocationResultDecoder[T: Decoder]: Decoder[ExternalInvocationResult[T]] = deriveDecoder

  implicit def resultContextEncoder[T: Encoder]: Encoder[ResultContext[T]] = deriveEncoder
  implicit def resultContextDecoder[T: Decoder]: Decoder[ResultContext[T]] = deriveDecoder

  implicit def exceptionResultEncoder[T: Encoder]: Encoder[ExceptionResult[T]] = deriveEncoder
  implicit def exceptionResultDecoder[T: Decoder]: Decoder[ExceptionResult[T]] = deriveDecoder

  implicit val nodeTransitionKeyEncoder: KeyEncoder[NodeTransition] =
    KeyEncoder.instance((nt: NodeTransition) => s"${nt.sourceNodeId}->${nt.destinationNodeId.getOrElse("")}")

  implicit val nodeTransitionKeyDecoder: KeyDecoder[NodeTransition] =
    KeyDecoder.instance { str =>
      str.split("->", 2).toList match {
        case source :: dest :: Nil =>
          Some(NodeTransition(source, if (dest.isEmpty) None else Some(dest)))
        case _ => None
      }
    }

  implicit def testResultsEncoder[T: Encoder]: Encoder[TestResults[T]] = deriveEncoder
  implicit def testResultsDecoder[T: Decoder]: Decoder[TestResults[T]] = deriveDecoder

  implicit val collectedLiveDataEncoder: Encoder[CollectedLiveData] = deriveEncoder
  implicit val collectedLiveDataDecoder: Decoder[CollectedLiveData] = deriveDecoder

}
