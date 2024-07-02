package pl.touk.nussknacker.engine.util

import io.circe.generic.JsonCodec

@JsonCodec
final case class CaretPosition2d(row: Int, column: Int) {

  def normalizedCaretPosition(inputValue: String): Int = {
    inputValue.split("\n").take(row).map(_.length).sum + row + column
  }

}
