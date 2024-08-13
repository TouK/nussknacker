package pl.touk.nussknacker.test

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class NuRestAssureMatchersSpec extends AnyFunSuiteLike with Matchers {

  private val currentJson = s"""{
                               |  "a": "Astring",
                               |  "b": true,
                               |  "c": 1,
                               |  "d": null,
                               |  "e": [ "a", 1, true, [], null, {} ],
                               |  "f": {},
                               |  "g": {
                               |    "a": "Bstring",
                               |    "b": false,
                               |    "c": 2,
                               |    "d": null,
                               |    "e": [ "a", 1, true, [], null, {} ],
                               |    "f": {}
                               |  }
                               |}""".stripMargin

  private val newlineDelimitedJsons =
    """{ "a": "Astring" }
      |{ "a": "Bstring" }""".stripMargin

  test("it should match the current JSON") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "^\\\\wstring$$",
           |  "b": "^true|false$$",
           |  "c": "^\\\\d{1}\\\\.0$$",
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "^\\\\wstring$$",
           |    "b": "^true|false$$",
           |    "c": "^\\\\d{1}\\\\.0$$",
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(true)
  }

  test("it should not match when string value field in root level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "^\\\\w\\\\w$$",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when string value field in nested level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "^\\\\w\\\\w$$",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when boolean value field in root level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": "^false$$",
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when boolean value field in nested level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": "^true$$",
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when number value field in root level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": "^\\\\d{2}\\\\.0$$",
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when number value field in nested level is not matched") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": "^\\\\d{2}\\\\.0$$",
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in root level is not matched (because of different order of elements)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ 1, "a", true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in root level is not matched (because of different count of elements)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in root level is not matched (because of object in array is different)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, { "a": true } ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in nested level is not matched (because of different order of elements)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", true, 1, [], null, {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in nested level is not matched (because of different count of elements)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], {} ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test(
    "it should not match when array value field in nested level is not matched (because of object in array is different)"
  ) {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, { "a": true } ],
           |    "f": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when expected JSON has more fields in root level") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {}
           |  },
           |  "h": {}
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should not match when expected JSON has more fields in nested level") {
    NuRestAssureMatchers
      .matchJsonWithRegexValues(
        s"""{
           |  "a": "Astring",
           |  "b": true,
           |  "c": 1,
           |  "d": null,
           |  "e": [ "a", 1, true, [], null, {} ],
           |  "f": {},
           |  "g": {
           |    "a": "Bstring",
           |    "b": false,
           |    "c": 2,
           |    "d": null,
           |    "e": [ "a", 1, true, [], null, {} ],
           |    "f": {},
           |    "g": {}
           |  }
           |}""".stripMargin
      )
      .matches(currentJson) should be(false)
  }

  test("it should match current line delimited jsons") {
    NuRestAssureMatchers
      .matchAllNdJsonWithRegexValues(
        s"""{
         |  "a": "^\\\\wstring$$"
         |}""".stripMargin
      )
      .matches(newlineDelimitedJsons) should be(true)
  }

  test("it should not match current line delimited jsons when any json in not matched") {
    NuRestAssureMatchers
      .matchAllNdJsonWithRegexValues(
        s"""{
         |  "a": "Astring"
         |}""".stripMargin
      )
      .matches(newlineDelimitedJsons) should be(false)
  }

}
