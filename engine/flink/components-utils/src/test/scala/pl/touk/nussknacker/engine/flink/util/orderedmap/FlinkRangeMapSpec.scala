package pl.touk.nussknacker.engine.flink.util.orderedmap

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap._

import java.{util => jul}
import scala.collection.immutable.SortedMap
import scala.language.higherKinds

class FlinkRangeMapSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  test("adding") {
    verifyAdding[SortedMap]
  }

  private def verifyAdding[MapT[_, _]: FlinkRangeMap] = {

    implicitly[FlinkRangeMap[MapT]].empty[Int, Int]
      .updated(1, 0)
      .toScalaMapRO.size shouldEqual 1
  }

  test("read-only filtering") {
    verifyReadOnlyFiltering[SortedMap](shouldThrowExceptionAfterROFiltering = false)
  }

  test("ordering") {
    verifyOrder[SortedMap]
  }

  private def verifyOrder[MapT[_, _]: FlinkRangeMap] = {
    val withSomeElements = implicitly[FlinkRangeMap[MapT]].empty[Int, Int]
          .updated(10, 0)
          .updated(5, 0)
          .updated(20, 0)
    withSomeElements.toScalaMapRO.keys.toList shouldBe List(5, 10, 20)
  }

  private def verifyReadOnlyFiltering[MapT[_, _]: FlinkRangeMap](shouldThrowExceptionAfterROFiltering: Boolean) = {
    val withSomeElements = implicitly[FlinkRangeMap[MapT]].empty[Int, Int]
      .updated(1, 0)
      .updated(2, 0)
      .updated(3, 0)

    val filteredByRangeRO = withSomeElements.fromRO(2).toRO(2)

    filteredByRangeRO.toScalaMapRO shouldEqual Map(2 -> 0)
    withSomeElements.toScalaMapRO shouldEqual Map(1 -> 0, 2 -> 0, 3 -> 0)

    if (shouldThrowExceptionAfterROFiltering) {
      an[UnsupportedOperationException] shouldBe thrownBy {
        filteredByRangeRO.updated(4, 0)
      }
    }
  }

  test("mutating filtering") {
    verifyMutatingFiltering[SortedMap]
  }

  private def verifyMutatingFiltering[MapT[_, _]: FlinkRangeMap] = {
    val withSomeElements = implicitly[FlinkRangeMap[MapT]].empty[Int, Int]
      .updated(1, 0)
      .updated(2, 0)
      .updated(3, 0)

    val filteredByRange = withSomeElements.from(2).to(2)

    filteredByRange.toScalaMapRO shouldEqual Map(2 -> 0)

    val withAddedEntryAfterFiltering = filteredByRange.updated(4, 0)
    withAddedEntryAfterFiltering.toScalaMapRO shouldEqual Map(2 -> 0, 4 -> 0)
  }

}
