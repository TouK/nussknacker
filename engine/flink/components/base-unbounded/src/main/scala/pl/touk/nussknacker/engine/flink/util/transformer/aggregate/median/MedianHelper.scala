package pl.touk.nussknacker.engine.flink.util.transformer.aggregate.median

import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.{
  ForLargeFloatingNumbersOperation,
}
import pl.touk.nussknacker.engine.util.MathUtils

import scala.annotation.tailrec
import scala.util.Random

object MedianHelper {
  private val rand = new Random(42)

  def calculateMedian(numbers: List[Number]): Option[Number] = {
    if (numbers.isEmpty) {
      None
    } else if (numbers.size % 2 == 1) {
      Some(MathUtils.convertToPromotedType(quickSelect(numbers, (numbers.size - 1) / 2))(ForLargeFloatingNumbersOperation))
    } else {
      // it is possible to fetch both numbers with single recursion, but it would complicate code
      val firstNumber  = quickSelect(numbers, numbers.size / 2 - 1)
      val secondNumber = quickSelect(numbers, numbers.size / 2)

      val sum = MathUtils.largeFloatingSum(firstNumber, secondNumber)
      Some(MathUtils.divideWithDefaultBigDecimalScale(sum, 2))
    }
  }

  //  https://en.wikipedia.org/wiki/Quickselect
  @tailrec
  private def quickSelect(numbers: List[Number], indexToTake: Int): Number = {
    require(numbers.nonEmpty)
    require(indexToTake >= 0)
    require(indexToTake < numbers.size)

    val randomElement = numbers(rand.nextInt(numbers.size))
    val groupedBy = numbers.groupBy(e => {
      val cmp = MathUtils.compare(e, randomElement)
      if (cmp < 0) {
        -1
      } else if (cmp == 0) {
        0
      } else 1
    })
    val smallerNumbers = groupedBy.getOrElse(-1, Nil)
    val equalNumbers   = groupedBy.getOrElse(0, Nil)
    val largerNumbers  = groupedBy.getOrElse(1, Nil)

    if (indexToTake < smallerNumbers.size) {
      quickSelect(smallerNumbers, indexToTake)
    } else if (indexToTake < smallerNumbers.size + equalNumbers.size) {
      equalNumbers(indexToTake - smallerNumbers.size)
    } else {
      quickSelect(largerNumbers, indexToTake - smallerNumbers.size - equalNumbers.size)
    }
  }

}
