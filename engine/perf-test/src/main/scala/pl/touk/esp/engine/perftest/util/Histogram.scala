package pl.touk.esp.engine.perftest.util

import scala.annotation.tailrec

case class Histogram[T: Ordering](private val list: List[T]) {

  @tailrec
  final def withValues(values: T*): Histogram[T] = {
    values.toList match {
      case head :: tail =>
        withValue(head).withValues(tail: _*)
      case Nil =>
        this
    }
  }

  def withValue(value: T): Histogram[T] =
    copy(value :: list)

  def min = {
    list.toIndexedSeq.sorted.head
  }

  def max = {
    list.toIndexedSeq.sorted.last
  }

  def mean = {
    percentile(50.0)
  }

  def percentile(p: Double) = {
    require(p > 0.0, "Percentile must be greater than 0")
    require(p <= 100.0, "Percentile must be lower or equals to 100")
    val seq = list.toIndexedSeq.sorted
    val n =  Math.ceil(seq.size * p / 100).toInt - 1
    seq(n)
  }

  def show: String =
    s"min: $min, mean: $mean, 75%: ${percentile(75.0)}, 90%: ${percentile(90.0)}, 95%: ${percentile(95.0)}, max: $max"

}

object Histogram {

  def empty[T: Ordering] = {
    Histogram(List.empty)
  }

}