package pl.touk.nussknacker.engine.util.cache

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class LazyMapSpec extends AnyFunSuite with Matchers {

  val keySet  = Set("a", "bb", "ccc", "dddd")
  val lazyMap = new LazyMap[String, Integer](keySet.asJava, _.length)

  val exception    = new RuntimeException("message")
  val exceptionMap = new LazyMap[String, Integer](keySet.asJava, _ => throw exception)

  test("map interface works correctly") {

    lazyMap.size shouldBe 4
    lazyMap.get("bb") shouldBe 2
    lazyMap.isEmpty() shouldBe false
    lazyMap.keySet() shouldBe keySet.asJava
    lazyMap.containsKey("ccc") shouldBe true
    lazyMap.containsKey("XXX") shouldBe false
    lazyMap.entrySet() shouldBe keySet
      .map(x => x -> x.length)
      .map { case (key, value) => java.util.Map.entry(key, value) }
      .asJava
    lazyMap.containsValue(4) shouldBe true
    lazyMap.containsValue(40) shouldBe false

    intercept[java.lang.UnsupportedOperationException](lazyMap.put("testKey", 30))
  }

  test("map unpack root exception") {

    val ex = intercept[Exception] { exceptionMap.get("a") }

    exceptionMap.size shouldBe 4
    ex shouldBe exception
  }

}
