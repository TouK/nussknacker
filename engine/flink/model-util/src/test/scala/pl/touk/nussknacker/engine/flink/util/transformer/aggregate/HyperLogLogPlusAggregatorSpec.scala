package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.{FunSuite, Matchers}

import scala.util.Random

class HyperLogLogPlusAggregatorSpec extends FunSuite with Matchers {

  //the aim of this test is to be able to test different parameters easily
  test("run alg for different params") {

    val set1 = generateRandomData(unique = 20, randomRepeat = 5)
    val set2 = generateRandomData(unique = 100, randomRepeat = 20)
    val set3 = generateRandomData(unique = 1000, randomRepeat = 30)


    val lowerPrecision = HyperLogLogPlusAggregator(5, 10)
    runForData(lowerPrecision, set1) shouldBe (19, 33)
    runForData(lowerPrecision, set2) shouldBe (86, 32)
    runForData(lowerPrecision, set3) shouldBe (1476, 32)

    val higherPrecision = HyperLogLogPlusAggregator(10, 15)
    runForData(higherPrecision, set1) shouldBe (20, 47)
    runForData(higherPrecision, set2) shouldBe (100, 200)
    runForData(higherPrecision, set3) shouldBe (1033, 693)

  }

  private def generateRandomData(unique: Int, randomRepeat: Int) = {
    Random.shuffle((1 to unique).flatMap(k => (0 to Random.nextInt(randomRepeat)).map(_ => k.toString)))
  }

  private def runForData(agg: HyperLogLogPlusAggregator, data: Iterable[AnyRef]): (Long, Int) = {
    val total = data.foldRight(agg.zero)(agg.addElement)
    (agg.result(total), total.wrapped.getBytes.length)
  }


}
