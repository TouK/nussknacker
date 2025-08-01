package pl.touk.nussknacker.engine.language.json

import io.circe.ParsingFailure
import org.typelevel.jawn.ParseException
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}

object JsonParsingFailureToExpressionParseErrorConverter {

  private val messageWithCoordinatesRegex = " \\(line \\d+, column \\d+\\)".r

  implicit class ParsingFailureExt(failure: ParsingFailure) {

    def toExpressionParseError: ExpressionParseError = {
      failure match {
        case ParsingFailure(message, underlying: ParseException) =>
          val messageWithoutCoordinates = messageWithCoordinatesRegex.replaceFirstIn(message, "")
          JsonParseError(
            messageWithoutCoordinates,
            Some(
              CoordinatesBasedTextRange(
                TextCoordinates(underlying.col - 1, underlying.line - 1),
                TextCoordinates(underlying.col, underlying.line - 1)
              )
            )
          )
        case ParsingFailure(message, _) => JsonParseError(message, None)
      }
    }

  }

  private[json] case class JsonParseError(
      override val message: String,
      override val details: Option[CoordinatesBasedTextRange]
  ) extends ExpressionParseError

}
