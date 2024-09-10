package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.dict.{DictInstance, UiDictServices}
import pl.touk.nussknacker.engine.api.generics.{MethodTypeInfo, Parameter => GenericsParameter}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Documentation, VariableConstants}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  ClassDefinitionExtractor,
  ClassDefinitionSet,
  StaticMethodDefinition
}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.{SimpleDictQueryService, SimpleDictRegistry}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, Parameter}
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.CaretPosition2d
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.ExpressionSuggesterTestData._
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester

import java.time.{Duration, LocalDateTime}
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class A {
  def foo(): A = this

  def fooString(): String = ""

  def barB(): B = new B
}

class B {
  def bazC(): C = new C

  def bazCWithParams(string1: String, string2: String, int: Int): C = new C
}

class C {
  def quaxString(): String = ""
}

class AA {
  def fooString(): String = ""

  def barB(): B = new B
}

class WithList {
  def listField(): java.util.List[A] = List(new A).asJava
}

class Util {
  def now(): LocalDateTime = LocalDateTime.now()

  @Documentation(description = "Method with description")
  def withDescription(): Int = 42
}

class ExpressionSuggesterSpec
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with TableDrivenPropertyChecks {
  val classExtractionSettings: ClassExtractionSettings = ClassExtractionSettings.Default
  val classDefinitionExtractor                         = new ClassDefinitionExtractor(classExtractionSettings)

  private val dictRegistry = new SimpleDictRegistry(
    Map(
      "dictFoo" -> EmbeddedDictDefinition(Map("one" -> "One", "two" -> "Two")),
      "dictBar" -> EmbeddedDictDefinition(Map("sentence-with-spaces-and-dots" -> "Sentence with spaces and . dots")),
    )
  )

  private val dictServices = UiDictServices(dictRegistry, new SimpleDictQueryService(dictRegistry, 10))

  private val clazzDefinitions: ClassDefinitionSet = ClassDefinitionSet(
    Set(
      classDefinitionExtractor.extract(classOf[A]),
      classDefinitionExtractor.extract(classOf[B]),
      classDefinitionExtractor.extract(classOf[C]),
      classDefinitionExtractor.extract(classOf[AA]),
      classDefinitionExtractor.extract(classOf[WithList]),
      ClassDefinition(
        Typed.typedClass[String],
        Map(
          "toUpperCase" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toUpperCase", None))
        ),
        Map.empty
      ),
      ClassDefinition(
        Typed.typedClass[LocalDateTime],
        Map(
          "isBefore" -> List(
            StaticMethodDefinition(
              MethodTypeInfo(List(GenericsParameter("arg0", Typed[LocalDateTime])), None, Typed[Boolean]),
              "isBefore",
              None
            )
          )
        ),
        Map.empty
      ),
      classDefinitionExtractor.extract(classOf[Util]),
      classDefinitionExtractor.extract(classOf[Duration]),
      ClassDefinition(
        Typed.typedClass[java.util.Map[_, _]],
        Map("empty" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Boolean]), "empty", None))),
        Map.empty
      ),
    )
  )

  private val expressionConfig: ExpressionConfigDefinition =
    ModelDefinitionBuilder.empty
      .withGlobalVariable("util", new Util)
      .build
      .expressionConfig

  private val expressionSuggester = new ExpressionSuggester(
    expressionConfig,
    clazzDefinitions,
    dictServices,
    getClass.getClassLoader,
    List("scenarioProperty")
  )

  private val localVariables: Map[String, TypingResult] = Map(
    "input"      -> Typed[A],
    "other"      -> Typed[C],
    "ANOTHER"    -> Typed[A],
    "dynamicMap" -> Typed.fromInstance(Map("intField" -> 1, "aField" -> new A)),
    "listVar"    -> Typed[WithList],
    "union" -> Typed(
      Typed[A],
      Typed[B],
      Typed[AA]
    ),
    "unionOfLists" -> Typed(
      Typed.genericTypeClass[java.util.List[A]](List(Typed[A])),
      Typed.genericTypeClass[java.util.List[B]](List(Typed[B])),
    ),
    "listOfUnions" -> Typed.genericTypeClass[java.util.List[A]](List(Typed(Typed[A], Typed[B]))),
    "dictFoo"      -> DictInstance("dictFoo", EmbeddedDictDefinition(Map.empty[String, String])).typingResult,
    "dictBar"      -> DictInstance("dictBar", EmbeddedDictDefinition(Map.empty[String, String])).typingResult,
  )

  private def spelSuggestionsFor(input: String, row: Int = 0, column: Int = -1): List[ExpressionSuggestion] = {
    suggestionsFor(Expression.spel(input), row, column)
  }

  private def spelTemplateSuggestionsFor(input: String, row: Int = 0, column: Int = -1): List[ExpressionSuggestion] = {
    suggestionsFor(Expression.spelTemplate(input), row, column)
  }

  private def suggestionsFor(expression: Expression, row: Int, column: Int): List[ExpressionSuggestion] = {
    expressionSuggester
      .expressionSuggestions(
        expression,
        CaretPosition2d(row, if (column == -1) expression.expression.length else column),
        localVariables
      )(ExecutionContext.global)
      .futureValue
  }

  private def suggestion(
      methodName: String,
      refClazz: TypingResult,
      description: Option[String] = None,
      parameters: List[Parameter] = Nil
  ): ExpressionSuggestion = {
    ExpressionSuggestion(methodName = methodName, refClazz, fromClass = false, description, parameters)
  }

  private val suggestionForBazCWithParams: ExpressionSuggestion = suggestion(
    "bazCWithParams",
    Typed[C],
    None,
    List(Parameter("string1", Typed[String]), Parameter("string2", Typed[String]), Parameter("int", Typed[Int]))
  )

  test("should not suggest anything for empty input") {
    spelSuggestionsFor("") shouldBe List()
  }

  test("should suggest all local and global variables if # specified") {
    spelSuggestionsFor("#").map(_.methodName) shouldBe List(
      "#ANOTHER",
      "#dictBar",
      "#dictFoo",
      "#dynamicMap",
      "#input",
      "#listOfUnions",
      "#listVar",
      s"#${VariableConstants.MetaVariableName}",
      "#other",
      "#union",
      "#unionOfLists",
      "#util"
    )
  }

  test("should suggest all local and global variables if # specified (multiline)") {
    spelSuggestionsFor("#foo.foo(\n#\n).bar", row = 1, column = 1).map(_.methodName) shouldBe List(
      "#ANOTHER",
      "#dictBar",
      "#dictFoo",
      "#dynamicMap",
      "#input",
      "#listOfUnions",
      "#listVar",
      s"#${VariableConstants.MetaVariableName}",
      "#other",
      "#union",
      "#unionOfLists",
      "#util"
    )
  }

  // TODO: add some score to each suggestion or sort them from most to least relevant
  test("should filter variables suggestions") {
    spelSuggestionsFor("#ot") shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
      suggestion("#other", Typed[C]),
    )
  }

  test("should filter uppercase variables suggestions") {
    spelSuggestionsFor("#ANO") shouldBe List(suggestion("#ANOTHER", Typed[A]))
  }

  test("should suggest filtered variable based not on beginning of the method") {
    spelSuggestionsFor("#map") shouldBe List(suggestion("#dynamicMap", localVariables("dynamicMap")))
  }

  test("should suggest variable") {
    spelSuggestionsFor("#inpu") shouldBe List(suggestion("#input", Typed[A]))
  }

  test("should suggest variable methods") {
    spelSuggestionsFor("#input.") shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for string literal") {
    spelSuggestionsFor("\"abc\".") shouldBe List(
      ExpressionSuggestion("toUpperCase", Typed[String], fromClass = false, None, Nil),
    )
  }

  test("should suggest fields and class methods for map literal") {
    spelSuggestionsFor("{\"abc\":1}.") shouldBe List(
      ExpressionSuggestion("abc", Typed.fromInstance(1), fromClass = false, None, Nil),
      ExpressionSuggestion("empty", Typed[Boolean], fromClass = true, None, Nil),
    )
  }

  test("should not suggest unreferenceable fields for map literal after dot") {
    nonStandardFieldNames.foreach { fieldName =>
      spelSuggestionsFor(s"{'$fieldName': 1}.") shouldBe List(
        ExpressionSuggestion("empty", Typed[Boolean], fromClass = true, None, Nil)
      )
    }
  }

  test("should suggest fields for map literal using indexing by property") {
    val expression = s"{key: 1}[k]"
    spelSuggestionsFor(expression, 0, expression.length - 1) shouldBe List(
      ExpressionSuggestion("key", Typed.fromInstance(1), fromClass = false, None, Nil)
    )
  }

  test("should suggest fields for map literal in indexer") {
    (nonStandardFieldNames ++ standardFieldNames ++ javaKeywordNames).foreach { fieldName =>
      val expression = s"{'$fieldName': 1}['']"
      spelSuggestionsFor(expression, 0, expression.length - 2) shouldBe List(
        ExpressionSuggestion(fieldName, TypedObjectWithValue(Typed.typedClass[Int], 1), fromClass = false, None, Nil)
      )
    }
  }

  test("should suggest dict variable methods") {
    spelSuggestionsFor("#dictFoo.").map(_.methodName) shouldBe List("One", "Two")
  }

  test("should suggest method with description") {
    spelSuggestionsFor("#util.with") shouldBe List(
      suggestion("withDescription", Typed[Int], Some("Method with description"))
    )
  }

  test("should suggest dict variable methods using indexer syntax") {
    val correctInputs = List(
      "#dictBar['']",
      "#dictBar['S']",
      "#dictBar['Sentence w']",
      "#dictBar['Sentence with spaces and . dots']"
    )
    correctInputs.foreach(inputValue => {
      spelSuggestionsFor(inputValue, 0, inputValue.length - 2).map(_.methodName) shouldBe List(
        "Sentence with spaces and . dots"
      )
    })
  }

  test("should suggest filtered variable methods") {
    spelSuggestionsFor("#input.fo") shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest global meta variable") {
    spelSuggestionsFor("#meta.") shouldBe List(
      ExpressionSuggestion("empty", Typed[Boolean], fromClass = true, None, Nil),
      suggestion("processName", Typed[String]),
      suggestion("properties", Typed.record(Map("scenarioProperty" -> Typed[String]))),
    )
  }

  test("should suggest filtered variable methods based not on beginning of the method") {
    spelSuggestionsFor("#input.string") shouldBe List(
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for object returned from method") {
    spelSuggestionsFor("#input.barB.bazC.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for union objects") {
    spelSuggestionsFor("#union.") shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("bazC", Typed[C]),
      suggestionForBazCWithParams,
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest methods for object returned from method from union objects") {
    spelSuggestionsFor("#union.bazC.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest in complex expression #1") {
    spelSuggestionsFor("#input.foo + #input.barB.bazC.quax", 0, "#input.foo".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest in complex expression #2") {
    spelSuggestionsFor("#input.foo + #input.barB.bazC.quax") shouldBe List(
      suggestion("quaxString", Typed[String])
    )
  }

  test("should suggest in complex expression #3") {
    spelSuggestionsFor("#input.barB.bazC.quaxString.toUp") shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should not suggest anything if suggestion already applied with space at the end") {
    spelSuggestionsFor("#input.fooString ") shouldBe Nil
  }

  test("should suggest for invocations with method parameters #1") {
    spelSuggestionsFor("#input.foo + #input.barB.bazCWithParams('1', '2', 3).quax") shouldBe List(
      suggestion("quaxString", Typed[String]),
    )
  }

  test("should suggest for invocations with method parameters #2") {
    spelSuggestionsFor(
      "#input.foo + #input.barB.bazCWithParams('1', #input.foo, 2).quax",
      0,
      "#input.foo + #input.barB.bazCWithParams('1', #input.foo".length
    ) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest for multiline code #1") {
    spelSuggestionsFor("#input\n.fo", 1, ".fo".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest for multiline code #2") {
    spelSuggestionsFor("#input\n.barB\n.", 2, ".".length) shouldBe List(
      suggestion("bazC", Typed[C]),
      suggestionForBazCWithParams,
      suggestion("toString", Typed[String]),
    )
  }

  test("should suggest for multiline code #3") {
    spelSuggestionsFor("#input\n.ba\n.barC", 1, ".ba".length) shouldBe List(suggestion("barB", Typed[B]))
  }

  test("should omit whitespace formatting in suggest for multiline code #1") {
    spelSuggestionsFor("#input\n  .ba", 1, "  .ba".length) shouldBe List(
      suggestion("barB", Typed[B]),
    )
  }

  test("should omit whitespace formatting in suggest for multiline code #2") {
    spelSuggestionsFor("#input\n  .barB\n  .ba", 2, "  .ba".length) shouldBe List(
      suggestion("bazC", Typed[C]),
      suggestionForBazCWithParams,
    )
  }

  test("should omit whitespace formatting in suggest for multiline code #3") {
    spelSuggestionsFor("#input\n  .ba\n  .bazC", 1, "  .ba".length) shouldBe List(suggestion("barB", Typed[B]))
  }

  test("should omit whitespace formatting in suggest for multiline code #4") {
    spelSuggestionsFor("#input\n  .barB.ba", 1, "  .barB.ba".length) shouldBe List(
      suggestion("bazC", Typed[C]),
      suggestionForBazCWithParams,
    )
  }

  test("should omit whitespace formatting in suggest for multiline code #5") {
    spelSuggestionsFor("#input\n  .barB.bazC\n  .quaxString.", 2, "  .quaxString.".length) shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should suggest field in typed map") {
    spelSuggestionsFor("#dynamicMap.int") shouldBe List(
      suggestion("intField", Typed.fromInstance(1)),
    )
  }

  test("should suggest embedded field in typed map") {
    spelSuggestionsFor("#dynamicMap.aField.f") shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest #this in list projection") {
    spelSuggestionsFor("#listVar.listField.![#thi]", 0, "#listVar.listField.![#thi".length) shouldBe List(
      suggestion("#this", Typed[A]),
    )
  }

  test("should suggest #this in list selection") {
    spelSuggestionsFor("#listVar.listField.?[#thi]", 0, "#listVar.listField.?[#thi".length) shouldBe List(
      suggestion("#this", Typed[A]),
    )
  }

  test("should suggest #this in literal list projection") {
    spelSuggestionsFor("{1,2,3}.![#thi]", 0, "{1,2,3}.![#thi".length) shouldBe List(
      suggestion("#this", Typed[Int]),
    )
  }

  test("should suggest #this in literal list selection") {
    spelSuggestionsFor("{1,2,3}.?[#thi]", 0, "{1,2,3}.?[#thi".length) shouldBe List(
      suggestion("#this", Typed[Int]),
    )
  }

  test("should suggest #this in map selection") {
    spelSuggestionsFor("{abc: 1, def: 'xyz'}.![#this]", 0, "{abc: 1, def: 'xyz'}.![#this".length) shouldBe List(
      suggestion("#this", Typed.record(ListMap("key" -> Typed[String], "value" -> Unknown))),
    )
  }

  test("should suggest #this fields in simple projection") {
    spelSuggestionsFor("#listVar.listField.![#this.f]", 0, "#listVar.listField.![#this.f".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest #this fields in projection on list of unions") {
    spelSuggestionsFor("#listOfUnions.![#this.f]", 0, "#unionOfLists.![#this.f".length) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest #this fields in projection after selection") {
    spelSuggestionsFor(
      "#listVar.listField.?[#this == 'value'].![#this.f]",
      0,
      "#listVar.listField.?[#this == 'value'].![#this.f".length
    ) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("handles negated parameters with projections and selections") {
    spelSuggestionsFor(
      "!#listVar.listField.?[#this == 'value'].![#this.f]",
      0,
      "!#listVar.listField.?[#this == 'value'].![#this.f".length
    ) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should suggest fields for first element of the list") {
    spelSuggestionsFor("#listVar.listField[ 0 ].f") shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should support nested method invocations") {
    spelSuggestionsFor(
      "#util.now(#other.quaxString.toUpperCase().)",
      0,
      "#util.now(#other.quaxString.toUpperCase().".length
    ) shouldBe List(
      suggestion("toUpperCase", Typed[String]),
    )
  }

  test("should support safe navigation") {
    spelSuggestionsFor("#input?.barB.bazC?.") shouldBe List(
      suggestion("quaxString", Typed[String]),
      suggestion("toString", Typed[String]),
    )
  }

  test("should support type reference and suggest methods") {
    spelSuggestionsFor("T(java.time.Duration).ZERO.plusD") shouldBe List(
      suggestion("plusDays", Typed[Duration], None, List(Parameter("arg0", Typed[Long]))),
    )
  }

  test("should support type reference and suggest static methods") {
    spelSuggestionsFor("T(java.time.Duration).p") shouldBe List(
      suggestion("parse", Typed[Duration], None, List(Parameter("arg0", Typed[CharSequence]))),
    )
  }

  test("should support type reference and suggest static fields") {
    spelSuggestionsFor("T(java.time.Duration).z") shouldBe List(
      suggestion("ZERO", Typed[Duration]),
    )
  }

  test("should suggest class package for type reference") {
    spelSuggestionsFor("T(j)", 0, "T(j".length) shouldBe List(
      suggestion("java", Unknown),
    )
    spelSuggestionsFor("T(java.t)", 0, "T(java.t".length) shouldBe List(
      suggestion("time", Unknown),
    )
  }

  test("should suggest classes for type reference") {
    spelSuggestionsFor("T(java.time.)", 0, "T(java.time.".length) shouldBe List(
      suggestion("Duration", Unknown),
      suggestion("LocalDateTime", Unknown),
    )
  }

  test("should filter suggestions for type reference") {
    spelSuggestionsFor("T(java.time.D)", 0, "T(java.time.D".length) shouldBe List(
      suggestion("Duration", Unknown),
    )
  }

  test("should not suggest completions when it infers that expression is enclosed in quotes") {
    spelSuggestionsFor(
      "{'ala', 'ma', '#'}",
      0,
      "{'ala', 'ma', '#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"'\", '\"', '#}",
      0,
      "{\"'\", '\"', '#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"'\", '\"', \"#}",
      0,
      "{\"'\", '\"', \"#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{'\"', '\'', \"#}",
      0,
      "{'\"', '\'', \"#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{'\"', '\'', '#}",
      0,
      "{'\"', '\'', '#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"\"\", \"\'\", '#}",
      0,
      "{\"\"\", \"\'\", '#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"\"\", \"\'\", \"#}",
      0,
      "{\"\"\", \"\'\", \"#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"\"\", '\'', '#}",
      0,
      "{\"\"\", '\'', '#".length
    ) shouldBe Nil

    spelSuggestionsFor(
      "{\"\"\", '\'', \"#}",
      0,
      "{\"\"\", '\'', \"#".length
    ) shouldBe Nil
  }

  test("should suggest completions of the previous token even if the whole expression is not proper SpEL expression") {
    spelSuggestionsFor(
      "#AN #hell",
      0,
      "#AN".length
    ) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )

    spelSuggestionsFor(
      "#AN (#hell)",
      0,
      "#AN".length
    ) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )

    spelSuggestionsFor(
      "#input. #ANOT",
      0,
      "#input.".length
    ) shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )

    spelSuggestionsFor(
      """
        |{
        |  foo: #inpu,
        |  bar: #input. (#input.fooSt)
        |}
        |""".stripMargin,
      3,
      "  bar: #input.".length
    ) shouldBe List(
      suggestion("barB", Typed[B]),
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
      suggestion("toString", Typed[String]),
    )

    spelSuggestionsFor(
      """
        |{
        |  foo: #inpu,
        |  bar: #listVar.listField[ 0 ].f (#input.fooSt)
        |}
        |""".stripMargin,
      3,
      "  bar: #listVar.listField[ 0 ].f".length
    ) shouldBe List(
      suggestion("foo", Typed[A]),
      suggestion("fooString", Typed[String]),
    )
  }

  test("should not throw exception for invalid spel expression") {
    spelSuggestionsFor("# - 2a") shouldBe Nil
    spelSuggestionsFor("foo") shouldBe Nil
    spelSuggestionsFor("##") shouldBe Nil
    spelSuggestionsFor("#bar.'abc") shouldBe Nil
  }

  test("should suggest variables for spel template") {
    spelTemplateSuggestionsFor("Hello #{#AN}", 0, "Hello #{#AN".length) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )
  }

  test("should suggest variables for spel template with spaces around") {
    spelTemplateSuggestionsFor(
      "#{        #hello        } - #{  #AN  }",
      0,
      "#{        #hello        } - #{  #AN".length
    ) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )
    spelTemplateSuggestionsFor(
      "#{#hello} - #{        #AN        }",
      0,
      "#{#hello} - #{        #AN".length
    ) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )
    spelTemplateSuggestionsFor(
      "#{    #hello    } - #{    #AN    }",
      0,
      "#{    #hello    } - #{    #AN".length
    ) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )
    spelTemplateSuggestionsFor("#{\n#hello\n} - #{\n\t#AN  }", 3, "\t#AN".length) shouldBe List(
      suggestion("#ANOTHER", Typed[A]),
    )
  }

  test("should not suggest expressions outside #{}") {
    spelTemplateSuggestionsFor("Hello #in", 0, "Hello #in".length) shouldBe List()
  }

  test("should suggest variables for second spel expression, even if the first one is invalid") {
    spelTemplateSuggestionsFor(
      s"Hello #{#invalidVar.foo} and #{#in}",
      0,
      "Hello #{#invalidVar.foo} and #{#in".length
    ) shouldBe List(
      suggestion("#input", Typed[A]),
    )
    spelTemplateSuggestionsFor(s"Hello #{!1 + a} and #{#in}", 0, "Hello #{!1 + a} and #{#in".length) shouldBe List(
      suggestion("#input", Typed[A]),
    )
  }

}

object ExpressionSuggesterTestData {

  val nonStandardFieldNames: List[String] = List("1", " ", "", "1.1", "?", "#", ".", " a ", "  a", "a  ")
  val javaKeywordNames: List[String]      = List("class", "null", "false")
  val standardFieldNames: List[String]    = List("key1")

}
