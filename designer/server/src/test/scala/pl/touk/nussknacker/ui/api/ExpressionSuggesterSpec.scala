package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ExpressionSuggesterSpec extends AnyFunSuite with Matchers {

  private val expressionSuggester = new ExpressionSuggester()

  private val variables: Map[String, RefClazz] = Map(
    "#input" -> RefClazz(refClazzName = Some("org.A")),
    "#other" -> RefClazz(refClazzName = Some("org.C")),
    "#ANOTHER" -> RefClazz(refClazzName = Some("org.A")),
    "#dynamicMap" -> RefClazz(
      refClazzName = Some("java.util.Map"),
      fields = Some(Map(
        "intField" -> RefClazz(refClazzName = Some("java.lang.Integer")),
        "aField" -> RefClazz(refClazzName = Some("org.A"))
      ))
    ),
    "#listVar" -> RefClazz(refClazzName = Some("org.WithList")),
    "#util" -> RefClazz(refClazzName = Some("org.Util")),
    "#union" -> RefClazz(
      Some("union"),
      union = Some(List(
        RefClazz(refClazzName = Some("org.A")),
        RefClazz(refClazzName = Some("org.B")),
        RefClazz(refClazzName = Some("org.AA"))
      ))
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
    suggestionsFor("#ot") shouldBe List(ExpressionSuggestion(methodName = "#other", refClazz = RefClazz(refClazzName = Some("org.C"))))
  }

  test("should filter uppercase global variables suggestions") {
    suggestionsFor("#ANO") shouldBe List(ExpressionSuggestion(methodName = "#ANOTHER", refClazz = RefClazz(refClazzName = Some("org.A"))))
  }

  test("should suggest global variable") {
    suggestionsFor("#inpu") shouldBe List(ExpressionSuggestion(methodName = "#input", refClazz = RefClazz(refClazzName = Some("org.A"))))
  }

  ignore("should suggest global variable methods") {
    suggestionsFor("#input.") shouldBe List(
      ExpressionSuggestion("fooString", RefClazz(Some("java.lang.String"))),
      ExpressionSuggestion("barB", RefClazz(Some("org.B")))
    )
  }
}
