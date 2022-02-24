package pl.touk.nussknacker.engine.kafka

import org.scalatest.{FunSuite, Matchers}

class ListUtilSpec extends FunSuite with Matchers {

  test("merges one list ") {
    ListUtil.mergeListsFromTopics(List(List("a", "b", "c", "d")), 3) shouldBe List("a", "b", "c")
  }

  test("merges list of same size") {
    ListUtil.mergeListsFromTopics(List(List("aa", "ab", "ac"), List("ba", "bb", "bc"), List("ca", "cb", "cc")), 3) shouldBe List("aa", "ba", "ca")
  }

  test("merges list of different size") {
    ListUtil.mergeListsFromTopics(List(List("aa", "ab", "ac"), List("ba"), List()), 5) shouldBe List("aa", "ba", "ab", "ac")
  }

}
