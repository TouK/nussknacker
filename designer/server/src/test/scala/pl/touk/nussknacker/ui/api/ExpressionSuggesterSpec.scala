package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.generics.{MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, StaticMethodInfo}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion

import java.time.LocalDateTime

class A {
  def foo(): A = this
  def fooString(): String = ""

  def barB(): B = new B
}

class B {
  def bazC(): C = new C
}

class C {
  def quaxString(): String = ""
}

class AA {
  def fooString(): String = ""

  def barB(): B = new B
}

class WithList(listField: List[A])

class Util {
  def now(): LocalDateTime = LocalDateTime.now()
}

class ExpressionSuggesterSpec extends AnyFunSuite with Matchers {
  implicit val classExtractionSettings: ClassExtractionSettings = ClassExtractionSettings.Default

  private val expressionSuggester = new ExpressionSuggester

  private val clazzDefinitions: TypeDefinitionSet = TypeDefinitionSet(Set(
    EspTypeUtils.clazzDefinition(classOf[A]),
    EspTypeUtils.clazzDefinition(classOf[B]),
    EspTypeUtils.clazzDefinition(classOf[C]),
    EspTypeUtils.clazzDefinition(classOf[AA]),
    EspTypeUtils.clazzDefinition(classOf[WithList]),
    ClazzDefinition(
      Typed.typedClass[String],
      Map("toUpperCase" -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "toUpperCase", None))),
      Map.empty
    ),
    ClazzDefinition(
      Typed.typedClass[LocalDateTime],
      Map("isBefore" -> List(StaticMethodInfo(MethodTypeInfo(List(Parameter("arg0", Typed[LocalDateTime])), None, Typed[Boolean]), "isBefore", None))),
      Map.empty
    ),
    EspTypeUtils.clazzDefinition(classOf[Util]),
  ))

  private val variables: Map[String, TypingResult] = Map(
    "input" -> Typed[A],
    "other" -> Typed[C],
    "ANOTHER" -> Typed[A],
    "dynamicMap" -> Typed.fromInstance(Map("intField" -> 1, "aField" -> new A)),
    "listVar" -> Typed[WithList],
    "util" -> Typed[Util],
    "union" -> Typed(
      Typed[A],
      Typed[B],
      Typed[AA]
    )
  )

  private def suggestionsFor(input: String, row: Int = 0, column: Int = -1): List[ExpressionSuggestion] = {
    expressionSuggester.expressionSuggestions(Expression.spel(input), CaretPosition2d(row, if (column == -1) input.length else column), variables, clazzDefinitions)
  }

  private def suggestion(methodName: String, refClazz: TypingResult): ExpressionSuggestion = {
    ExpressionSuggestion(methodName = methodName, refClazz, fromClass = false)
  }

  test("should not suggest anything for empty input") {
    suggestionsFor("") shouldBe List()
  }

  test("should suggest all global variables if # specified") {
    suggestionsFor("#").map(_.methodName).toSet shouldBe Set("#input", "#other", "#ANOTHER", "#dynamicMap", "#listVar", "#util", "#union")
  }

  test("should suggest all global variables if # specified (multiline)") {
    suggestionsFor("#foo.foo(\n#\n).bar", row = 1, column = 1).map(_.methodName).toSet shouldBe Set("#input", "#other", "#ANOTHER", "#dynamicMap", "#listVar", "#util", "#union")
  }

  // TODO: add some score to each suggestion or sort them from most to least relevant
  test("should filter global variables suggestions") {
    suggestionsFor("#ot") shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
      suggestion("#other", Typed[C]),
    )
  }

  test("should filter uppercase global variables suggestions") {
    suggestionsFor("#ANO") shouldBe List(suggestion("#ANOTHER", Typed[A]))
  }

  test("should suggest filtered global variable based not on beginning of the method") {
    suggestionsFor("#map") shouldBe List(suggestion("#dynamicMap", variables("dynamicMap")))
  }

  test("should suggest global variable") {
    suggestionsFor("#inpu") shouldBe List(suggestion("#input", Typed[A]))
  }

  test("should suggest global variable methods") {
    suggestionsFor("#input.") shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for string literal") {
    suggestionsFor("\"abc\".") shouldBe List(
      ExpressionSuggestion("toUpperCase", Typed[String], fromClass = false),
    )
  }

  //  test("should suggest dict variable methods") {
  //    val stubbedDictSuggestions = List(
  //      Map("key" -> "one", "label" -> "One"),
  //      Map("key" -> "two", "label" -> "Two")
  //    )
  //    suggestionsFor("#dict.", 0, stubbedDictSuggestions)
  //  }
  //
  //  test("should suggest dict variable methods using indexer syntax") {
  //    val stubbedDictSuggestions = List(Map("key" -> "sentence-with-spaces-and-dots", "label" -> "Sentence with spaces and . dots"))
  //
  //    val correctInputs = List(
  //      "#dict['",
  //      "#dict['S",
  //      "#dict['Sentence w",
  //      "#dict['Sentence with spaces and . dots"
  //    )
  //    correctInputs.map(inputValue => {
  //      suggestionsFor(inputValue, null, stubbedDictSuggestions)
  //    })
  //  }

  test("should suggest filtered global variable methods") {
    suggestionsFor("#input.fo") shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest filtered global variable methods based not on beginning of the method") {
    suggestionsFor("#input.string") shouldBe List(
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for object returned from method") {
    suggestionsFor("#input.barB.bazC.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for union objects") {
    suggestionsFor("#union.") shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("bazC", Typed[C]),
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for object returned from method from union objects") {
    suggestionsFor("#union.bazC.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest in complex expression #1") {
    suggestionsFor("#input.foo + #input.barB.bazC.quax", 0, "#input.foo".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest in complex expression #2") {
    suggestionsFor("#input.foo + #input.barB.bazC.quax") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest in complex expression #3") {
    suggestionsFor("#input.barB.bazC.quaxString.toUp") shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should not suggest anything if suggestion already applied with space at the end") {
    suggestionsFor("#input.fooString ") shouldBe Nil
  }

  test("should suggest for invocations with method parameters #1") {
    suggestionsFor("#input.foo + #input.barB.bazC('1').quax") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest for invocations with method parameters #2") {
    suggestionsFor("#input.foo + #input.barB.bazC('1', #input.foo, 2).quax") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest for multiline code #1") {
    suggestionsFor("#input\n.fo", 1, ".fo".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest for multiline code #2") {
    suggestionsFor("#input\n.barB\n.", 2, ".".length) shouldBe List(
      suggestion("bazC", Typed[C]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest for multiline code #3") {
    suggestionsFor("#input\n.ba\n.barC", 1, ".ba".length) shouldBe List(suggestion("barB", Typed[B]))
  }

  test("should omit whitespace formatting in suggest for multiline code #1") {
    suggestionsFor("#input\n  .ba", 1, "  .ba".length) shouldBe List(
      suggestion("barB", Typed[B]),
    )
  }

  test("should omit whitespace formatting in suggest for multiline code #2") {
    suggestionsFor("#input\n  .barB\n  .ba", 2, "  .ba".length) shouldBe List(
      suggestion("bazC", Typed[C]),
    )
  }

  test("should omit whitespace formatting in suggest for multiline code #3") {
    suggestionsFor("#input\n  .ba\n  .bazC", 1, "  .ba".length) shouldBe List(suggestion("barB", Typed[B]))
  }

  ignore("should omit whitespace formatting in suggest for multiline code #4") {
    suggestionsFor("#input\n  .barB.ba", 1, "  .barB.ba".length) shouldBe List(suggestion("barB",Typed[B]))
  }

  test("should omit whitespace formatting in suggest for multiline code #5") {
    suggestionsFor("#input\n  .barB.bazC\n  .quaxString.", 2, "  .quaxString.".length) shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should suggest field in typed map") {
    suggestionsFor("#dynamicMap.int") shouldBe List(
      suggestion("intField", Typed[Int]),
    )
  }

  test("should suggest embedded field in typed map") {
    suggestionsFor("#dynamicMap.aField.f") shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  ignore("should suggest #this fields in simple projection") {
    suggestionsFor("#listVar.listField.![#this.f]", 0, "#listVar.listField.![#this.f".length)
  }

  ignore("should suggest #this fields in projection on union of lists") {
    suggestionsFor("#unionOfLists.![#this.f]", 0, "#unionOfLists.![#this.f".length)
  }

  ignore("should suggest #this fields in projection after selection") {
    suggestionsFor("#listVar.listField.?[#this == 'value'].![#this.f]", 0, "#listVar.listField.?[#this == 'value'].![#this.f".length)
  }

  ignore("handles negated parameters with projections and selections") {
    suggestionsFor("!#listVar.listField.?[#this == 'value'].![#this.f]", 0, "!#listVar.listField.?[#this == 'value'].![#this.f".length)
  }

  test("should support nested method invocations") {
    suggestionsFor("#util.now(#other.quaxString.toUpperCase().)", 0, "#util.now(#other.quaxString.toUpperCase().".length) shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should support safe navigation") {
    suggestionsFor("#input?.barB.bazC?.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }
}
