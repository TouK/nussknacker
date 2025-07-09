package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}

case class IndexBasedTextRange(start: Int, end: Int) {

  def shiftRight(delta: Int): IndexBasedTextRange = IndexBasedTextRange(start + delta, end + delta)

  def containsPosition(position: Int): Boolean = {
    start <= position && position <= end
  }

  def toCoordinatesBasedTextRange(expression: String): CoordinatesBasedTextRange = {
    def computeTextCoordinates(text: String): TextCoordinates =
      TextCoordinates(
        column = text.length - 1 - text.lastIndexOf('\n'),
        row = text.count(_ == '\n')
      )

    val errorDetails = CoordinatesBasedTextRange(
      computeTextCoordinates(expression.take(start)),
      computeTextCoordinates(expression.take(end)),
    )
    errorDetails
  }

}
