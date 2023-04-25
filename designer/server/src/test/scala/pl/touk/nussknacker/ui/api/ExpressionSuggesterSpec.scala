package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ExpressionSuggesterSpec extends AnyFunSuite with Matchers {

  private val expressionSuggester = new ExpressionSuggester()

  private val variables: Map[String, RefClazz] = Map(
    "input" -> RefClazz(refClazzName = "org.A"),
    "other" -> RefClazz(refClazzName = "org.C"),
    "ANOTHER" -> RefClazz(refClazzName = "org.A"),
    "dynamicMap" -> RefClazz(
      refClazzName = "java.util.Map",
      fields = Map(
        "intField" -> RefClazz(refClazzName = "java.lang.Integer"),
        "aField" -> RefClazz(refClazzName = "org.A")
      )
    ),
    "listVar" -> RefClazz(refClazzName = "org.WithList"),
    "util" -> RefClazz(refClazzName = "org.Util"),
    "union" -> RefClazz(
      "union",
      union = List(
        RefClazz(refClazzName = "org.A"),
        RefClazz(refClazzName = "org.B"),
        RefClazz(refClazzName = "org.AA")
      )
    )
  )

  private def suggestionsFor(input: String, row: Int = 0, column: Int = -1): List[ExpressionSuggestion] = {
    expressionSuggester.expressionSuggestions(input, CaretPosition2d(row, if(column == -1) input.length else column), variables)
  }

  test("should not suggest anything for empty input") {
    suggestionsFor("") shouldBe List()
  }

  test("should suggest all global variables if # specified") {
    suggestionsFor("#").map(_.methodName).toSet shouldBe Set("#input", "#other", "#ANOTHER", "#dynamicMap", "#listVar", "#util", "#union")
  }

  test("should suggest all global variables if # specified (multiline)") {
    suggestionsFor("""asdasd
                      |#
                      |dsadasdas""".stripMargin, row = 1, column = 1).map(_.methodName).toSet  shouldBe Set("#input", "#other", "#ANOTHER", "#dynamicMap", "#listVar", "#util", "#union")
  }

  test("should filter global variables suggestions") {
    suggestionsFor("#ot") shouldBe List(ExpressionSuggestion(methodName = "#other", refClazz = RefClazz(refClazzName = "org.C")))
  }

  test("should filter uppercase global variables suggestions") {
    suggestionsFor("#ANO") shouldBe List(ExpressionSuggestion(methodName = "#ANOTHER", refClazz = RefClazz(refClazzName = "org.A")))
  }

  test("should suggest global variable") {
    suggestionsFor("#inpu") shouldBe List(ExpressionSuggestion(methodName = "#input", refClazz = RefClazz(refClazzName = "org.A")))
  }

  ignore("should suggest global variable methods") {
    suggestionsFor("#input.") shouldBe List(
      ExpressionSuggestion("fooString", RefClazz("java.lang.String")),
      ExpressionSuggestion("barB", RefClazz("org.B"))
    )
  }
}
