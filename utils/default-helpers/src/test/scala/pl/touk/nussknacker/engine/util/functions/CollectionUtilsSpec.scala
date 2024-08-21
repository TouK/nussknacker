package pl.touk.nussknacker.engine.util.functions

import cats.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.spel.Typer.SpelCompilationException

import java.text.ParseException
import scala.jdk.CollectionConverters._

class CollectionUtilsSpec extends AnyFunSuite with BaseSpelSpec with Matchers {

  test("concat") {
    evaluateAny("#COLLECTION.concat({},{})") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.concat({1,2,3},{2,3,4})") shouldBe List(1, 2, 3, 2, 3, 4).asJava
    evaluateAny("#COLLECTION.concat({'a'},{2})") shouldBe List("a", 2).asJava

    Table(
      ("expression", "expected"),
      ("#COLLECTION.concat({1}, {2})", "List[Integer]"),
      ("#COLLECTION.concat({1}, {2.1})", "List[Number]"),
      ("#COLLECTION.concat({1.0}, {2.1})", "List[Double]"),
      ("#COLLECTION.concat({1}, {'a'})", "List[Unknown]"),
      ("#COLLECTION.concat({{:}}, {{:}})", "List[Record{}]"),
      ("#COLLECTION.concat({{key: 1}}, {{key: 2}})", "List[Record{key: Integer}]"),
      ("#COLLECTION.concat({{key: 1}}, {{key: 'a'}})", "List[Unknown]"),
      ("#COLLECTION.concat({{key1: 1}}, {{key2: 'a'}})", "List[Record{key1: Integer, key2: String}]"),
      ("#COLLECTION.concat({#unknownMap},{#unknownMap})", "List[Map[Unknown,Unknown]]"),
      ("#COLLECTION.concat({{key:1}},{#unknownMap})", "List[Map[Unknown,Unknown]]"),
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = types) shouldBe expected.valid
    }
  }

  test("merge") {
    Table(
      ("expression", "expected"),
      ("#COLLECTION.merge({:}, {:})", Map.empty.asJava),
      ("#COLLECTION.merge({:}, {a: 2, c: 2})", Map("a" -> 2, "c" -> 2).asJava),
      ("#COLLECTION.merge({a: 1, b: 1}, {:})", Map("a" -> 1, "b" -> 1).asJava),
      ("#COLLECTION.merge({a: 1, b: 1}, {a: 2, c: 2})", Map("a" -> 2, "b" -> 1, "c" -> 2).asJava),
      ("#COLLECTION.merge({a: {x: 1}}, {a: {y: 1}})", Map("a" -> Map("y" -> 1).asJava).asJava),
    ).forEvery { (expression, expected) =>
      evaluateAny(expression) shouldBe expected
    }

    Table(
      ("expression", "expected"),
      (
        "#COLLECTION.merge({a:4,c:2,b:3,d:1},{f:'rr',e:'5'})",
        "Record{a: Integer(4), b: Integer(3), c: Integer(2), d: Integer(1), e: String(5), f: String(rr)}"
      ),
      ("#COLLECTION.merge({a:4},{e:'5',f:'rr'})", "Record{a: Integer(4), e: String(5), f: String(rr)}"),
      (
        "#COLLECTION.merge({a:4,b:3,c:2,d:1},{e:'5'})",
        "Record{a: Integer(4), b: Integer(3), c: Integer(2), d: Integer(1), e: String(5)}"
      ),
      (
        "#COLLECTION.merge({a:{innerA:10}},{b:{innerB:10}})",
        "Record{a: Record{innerA: Integer(10)}, b: Record{innerB: Integer(10)}}"
      ),
      (
        "#COLLECTION.merge({a:{innerA:{10,20}}},{b:{innerB:{20}}})",
        "Record{a: Record{innerA: List[Integer]({10, 20})}, b: Record{innerB: List[Integer]({20})}}"
      ),
      ("#COLLECTION.merge({a:4,b:3},{a:'5'})", "Record{a: String(5), b: Integer(3)}"),
      ("#COLLECTION.merge(#unknownMap,{a:'5'})", "Map[Unknown,Unknown]"),
      ("#COLLECTION.merge(#unknownMap,#unknownMap)", "Map[Unknown,Unknown]"),
      ("#COLLECTION.merge({a:'5'},#unknownMap)", "Map[Unknown,Unknown]"),
      ("#COLLECTION.merge(#stringMap,{a:'5'})", "Map[String,Unknown]"),
      ("#COLLECTION.merge(#typedMap,{a:'5'})", "Record{a: String(5), key: Integer(20)}"),
      ("#COLLECTION.merge({b:'50'}, #typedMap)", "Record{b: String(50), key: Integer(20)}"),
    ).forEvery { (expression, expected) =>
      evaluateType(expression, types = types) shouldBe expected.valid
    }
  }

  test("min") {
    evaluateAny("#COLLECTION.min({1, 2, 3})") shouldBe 1
    evaluateAny("#COLLECTION.min({1.0, -2.2, 3.0})") shouldBe -2.2
    evaluateAny("#COLLECTION.min({'a', 'b', 'c'})") shouldBe "a"

    evaluateType("#COLLECTION.min({1, 2, 3})") shouldBe "Integer".valid
    evaluateType("#COLLECTION.min({1.0, -2.2, 3.0})") shouldBe "Double".valid
    evaluateType("#COLLECTION.min({'a', 'b', 'c'})") shouldBe "String".valid
  }

  test("max") {
    evaluateAny("#COLLECTION.max({1, 2, 3})") shouldBe 3
    evaluateAny("#COLLECTION.max({1.0, -2.2, 3.3})") shouldBe 3.3
    evaluateAny("#COLLECTION.max({'a', 'b', 'c'})") shouldBe "c"

    evaluateType("#COLLECTION.min({1, 2, 3})") shouldBe "Integer".valid
    evaluateType("#COLLECTION.min({1.0, -2.2, 3.0})") shouldBe "Double".valid
    evaluateType("#COLLECTION.min({'a', 'b', 'c'})") shouldBe "String".valid
  }

  test("slice") {
    evaluateAny("#COLLECTION.slice({1, 2, 3}, 0, 2)") shouldBe List(1, 2).asJava
    evaluateAny("#COLLECTION.slice({1, 2, 3}, 0, 10)") shouldBe List(1, 2, 3).asJava
    evaluateAny("#COLLECTION.slice({1, 2, 3}, 2, 0)") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.slice({1, 2, 3}, -2, 0)") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.slice({1, 2, 3}, -2, 1)") shouldBe List(1).asJava

    evaluateType("#COLLECTION.slice({{a: 1}}, -2, 1)") shouldBe "List[Record{a: Integer}]".valid
  }

  test(
    "sum for given empty list should return 0.0 (Double) - we are not able to determine the expected type for an empty list"
  ) {
    val lists = Map(
      "byte"   -> List.empty[java.lang.Byte].asJava,
      "short"  -> List.empty[java.lang.Short].asJava,
      "int"    -> List.empty[java.lang.Integer].asJava,
      "long"   -> List.empty[java.lang.Long].asJava,
      "bigInt" -> List.empty[java.math.BigInteger].asJava,
      "float"  -> List.empty[java.lang.Float].asJava,
      "double" -> List.empty[java.lang.Double].asJava,
      "bigDec" -> List.empty[java.math.BigDecimal].asJava,
    )

    evaluateAny("#COLLECTION.sum({})") shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#byte)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#short)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#int)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#long)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#bigInt)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#float)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#double)", lists) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum(#bigDec)", lists) shouldBe a[java.lang.Double]
  }

  test("sum should coerce to a compatible type of highest precision") {
    val variables = Map(
      "byte"   -> 1.byteValue(),
      "short"  -> 1.shortValue(),
      "int"    -> 1,
      "long"   -> 1L,
      "bigInt" -> java.math.BigInteger.valueOf(1),
      "float"  -> 1.0f,
      "double" -> 1.0,
      "bigDec" -> java.math.BigDecimal.valueOf(1),
    )

    evaluateAny("#COLLECTION.sum({#byte})", variables) shouldBe a[java.lang.Long]
    evaluateAny("#COLLECTION.sum({#short})", variables) shouldBe a[java.lang.Long]
    evaluateAny("#COLLECTION.sum({#int})", variables) shouldBe a[java.lang.Long]
    evaluateAny("#COLLECTION.sum({#long})", variables) shouldBe a[java.lang.Long]
    evaluateAny("#COLLECTION.sum({#bigInt})", variables) shouldBe a[java.math.BigInteger]
    evaluateAny("#COLLECTION.sum({#float})", variables) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum({#double})", variables) shouldBe a[java.lang.Double]
    evaluateAny("#COLLECTION.sum({#bigDec})", variables) shouldBe a[java.math.BigDecimal]

    evaluateType("#COLLECTION.sum({})", variables) shouldBe "Unknown".valid
    evaluateType("#COLLECTION.sum({#byte})", variables) shouldBe "Long".valid
    evaluateType("#COLLECTION.sum({#short})", variables) shouldBe "Long".valid
    evaluateType("#COLLECTION.sum({#int})", variables) shouldBe "Long".valid
    evaluateType("#COLLECTION.sum({#long})", variables) shouldBe "Long".valid
    evaluateType("#COLLECTION.sum({#bigInt})", variables) shouldBe "BigInteger".valid
    evaluateType("#COLLECTION.sum({#float})", variables) shouldBe "Double".valid
    evaluateType("#COLLECTION.sum({#double})", variables) shouldBe "Double".valid
    evaluateType("#COLLECTION.sum({#bigDec})", variables) shouldBe "BigDecimal".valid

    evaluateAny("#COLLECTION.sum({#bigDec})", variables) shouldEqual java.math.BigDecimal.valueOf(1)
    evaluateAny("#COLLECTION.sum({#double})", variables) shouldBe 1.0
    evaluateAny("#COLLECTION.sum({#float})", variables) shouldBe 1.0f
    evaluateAny("#COLLECTION.sum({#bigInt})", variables) shouldBe java.math.BigInteger.valueOf(1)
    evaluateAny("#COLLECTION.sum({#long})", variables) shouldBe 1L
    evaluateAny("#COLLECTION.sum({#int})", variables) shouldBe 1
    evaluateAny("#COLLECTION.sum({#short})", variables) shouldBe 1.shortValue()
    evaluateAny("#COLLECTION.sum({#byte})", variables) shouldBe 1.byteValue()

    evaluateAny("#COLLECTION.sum({#byte, #short, #int, #long, #bigInt, #float, #double, #bigDec})", variables)
      .asInstanceOf[java.math.BigDecimal]
      .compareTo(java.math.BigDecimal.valueOf(8.0)) shouldBe 0
    evaluateAny("#COLLECTION.sum({#byte, #short, #int, #long, #bigInt, #float, #double})", variables) shouldBe 7.0
    evaluateAny("#COLLECTION.sum({#byte, #short, #int, #long, #bigInt, #float})", variables) shouldBe 6.0
    evaluateAny("#COLLECTION.sum({#byte, #short, #int, #long, #bigInt})", variables) shouldBe java.math.BigInteger
      .valueOf(5)
    evaluateAny("#COLLECTION.sum({#byte, #short, #int, #long})", variables) shouldBe 4L
    evaluateAny("#COLLECTION.sum({#byte, #short, #int})", variables) shouldBe 3L
    evaluateAny("#COLLECTION.sum({#byte, #short})", variables) shouldBe 2L
    evaluateAny("#COLLECTION.sum({#byte})", variables) shouldBe 1L
    evaluateAny("#COLLECTION.sum({})", variables) shouldBe 0

    // There is no other way to type mixed content than as a Number
    evaluateType("#COLLECTION.sum({#int, #long})", variables) shouldBe "Number".valid
  }

  test("sum should fall back to Number on unknown Number type") {
    val custom = new CustomNumber()

    val numericalVariables = Map(
      "int"    -> 1,
      "long"   -> 1L,
      "double" -> 14.23d,
      "bigDec" -> new java.math.BigDecimal("1"),
      "bigInt" -> new java.math.BigInteger("1"),
      "custom" -> custom
    )

    evaluateType("#COLLECTION.sum({#int, #custom})", numericalVariables) shouldBe "Number".valid
    evaluateType("#COLLECTION.sum({#long, #custom})", numericalVariables) shouldBe "Number".valid
    evaluateType("#COLLECTION.sum({#double, #custom})", numericalVariables) shouldBe "Number".valid
    evaluateType("#COLLECTION.sum({#bigDec, #custom})", numericalVariables) shouldBe "Number".valid
    evaluateType("#COLLECTION.sum({#bigInt, #custom})", numericalVariables) shouldBe "Number".valid
  }

  test("plus should fall back to Number on unknown Number type") {
    val custom = new CustomNumber()

    val numericalVariables = Map(
      "int"    -> 1,
      "long"   -> 1L,
      "double" -> 14.23d,
      "bigDec" -> new java.math.BigDecimal("1"),
      "bigInt" -> new java.math.BigInteger("1"),
      "custom" -> custom
    )

    evaluateType("#custom + #int", numericalVariables) shouldBe "Number".valid
    evaluateType("#int + #custom", numericalVariables) shouldBe "Number".valid

    evaluateType("#custom + #long", numericalVariables) shouldBe "Number".valid
    evaluateType("#long + #custom", numericalVariables) shouldBe "Number".valid

    evaluateType("#custom + #double", numericalVariables) shouldBe "Number".valid
    evaluateType("#double+ #custom", numericalVariables) shouldBe "Number".valid

    evaluateType("#custom + #bigDec", numericalVariables) shouldBe "Number".valid
    evaluateType("#bigDec + #custom", numericalVariables) shouldBe "Number".valid

    evaluateType("#custom + #bigInt", numericalVariables) shouldBe "Number".valid
    evaluateType("#bigInt + #custom", numericalVariables) shouldBe "Number".valid
  }

  test("sortedAsc") {
    evaluateAny("#COLLECTION.sortedAsc({})") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.sortedAsc({4, 2, 1, 9})") shouldBe List(1, 2, 4, 9).asJava
  }

  test("sortedDesc") {
    evaluateAny("#COLLECTION.sortedDesc({})") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.sortedDesc({4, 2, 1, 9})") shouldBe List(9, 4, 2, 1).asJava
  }

  test("take") {
    evaluateAny("#COLLECTION.take({1, 2, 3}, 0)") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.take({1, 2, 3}, 1)") shouldBe List(1).asJava
    evaluateAny("#COLLECTION.take({1, 2, 3}, 3)") shouldBe List(1, 2, 3).asJava
    evaluateAny("#COLLECTION.take({1, 2, 3}, 4)") shouldBe List(1, 2, 3).asJava
    evaluateAny("#COLLECTION.take({1, 2, 3}, -1)") shouldBe List.empty.asJava
  }

  test("takeLast") {
    evaluateAny("#COLLECTION.takeLast({1, 2, 3}, 0)") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.takeLast({1, 2, 3}, 1)") shouldBe List(3).asJava
    evaluateAny("#COLLECTION.takeLast({1, 2, 3}, 3)") shouldBe List(1, 2, 3).asJava
    evaluateAny("#COLLECTION.takeLast({1, 2, 3}, 4)") shouldBe List(1, 2, 3).asJava
    evaluateAny("#COLLECTION.takeLast({1, 2, 3}, -1)") shouldBe List.empty.asJava
  }

  test("join") {
    evaluateAny("#COLLECTION.join({}, '')") shouldBe ""
    evaluateAny("#COLLECTION.join({1, 2, 3}, '')") shouldBe "123"
    evaluateAny("#COLLECTION.join({1, 2, 3}, ', ')") shouldBe "1, 2, 3"
  }

  test("product") {
    evaluateAny("#COLLECTION.product({{a: 'a'},{b: 'b'}}, {{c: 'c'},{d: 'd'}})") shouldBe List(
      Map("a" -> "a", "c" -> "c").asJava,
      Map("a" -> "a", "d" -> "d").asJava,
      Map("b" -> "b", "c" -> "c").asJava,
      Map("b" -> "b", "d" -> "d").asJava,
    ).asJava

    evaluateType(
      "#COLLECTION.product({{a: 'a'},{b: 'b'}}, {{c: 'c'},{d: 'd'}})"
    ) shouldBe "List[Map[Unknown,Unknown]]".valid
  }

  test("diff") {
    evaluateAny("#COLLECTION.diff({'1','2','3'},{'2','3','4'})") shouldBe List("1").asJava

    evaluateType("#COLLECTION.diff({'1','2','3'},{'2','3','4'})") shouldBe "List[String]".valid
    evaluateType("#COLLECTION.diff({1,2,3},{'2','3','4'})") shouldBe "List[Integer]".valid
  }

  test("intersect") {
    evaluateAny("#COLLECTION.intersect({'1','2','3'},{'2','3','4'})") shouldBe List("2", "3").asJava

    evaluateType("#COLLECTION.intersect({'1','2','3'},{'2','3','4'})") shouldBe "List[String]".valid
    evaluateType("#COLLECTION.intersect({1,2,3},{'2','3','4'})") shouldBe "List[Integer]".valid
  }

  test("distinct") {
    evaluateAny("#COLLECTION.distinct({'1','2','2','3'})") shouldBe List("1", "2", "3").asJava

    evaluateType("#COLLECTION.distinct({'1','2','3'})") shouldBe "List[String]".valid
    evaluateType("#COLLECTION.distinct({1,2,3})") shouldBe "List[Integer]".valid
  }

  test("shuffle") {
    evaluateType("#COLLECTION.shuffle({'1','2','3'})") shouldBe "List[String]".valid
    evaluateType("#COLLECTION.shuffle({1,2,3})") shouldBe "List[Integer]".valid
  }

  test("flatten") {
    evaluateAny("#COLLECTION.flatten({})") shouldBe List.empty.asJava
    evaluateAny("#COLLECTION.flatten({{'1'},{'2', '3'},{'3'}})") shouldBe List("1", "2", "3", "3").asJava
    evaluateAny("#COLLECTION.flatten({{{a:1},{b:2}},{{c:3},{d:4}}})") shouldBe
      List(Map("a" -> 1).asJava, Map("b" -> 2).asJava, Map("c" -> 3).asJava, Map("d" -> 4).asJava).asJava

    evaluateType("#COLLECTION.flatten({{'1'},{'2', '3'},{'3'}})") shouldBe "List[String]".valid
    evaluateType("#COLLECTION.flatten({{1},{2},{3}})") shouldBe "List[Integer]".valid
    evaluateType("#COLLECTION.flatten({{{a:1},{b:2}},{{c:3},{d:4}}})") shouldBe
      "List[Record{a: Integer, b: Integer, c: Integer, d: Integer}]".valid
  }

  test("sort by field") {
    evaluateAny(
      "#COLLECTION.sortedAscBy(#list, 'k')",
      Map(
        "list" -> List(
          Map("k" -> "v3").asJava,
          Map("k" -> "v1").asJava,
          Map("k" -> "v2").asJava,
        ).asJava,
      )
    ) shouldBe List(
      Map("k" -> "v1").asJava,
      Map("k" -> "v2").asJava,
      Map("k" -> "v3").asJava,
    ).asJava

    evaluateAny("#COLLECTION.sortedAscBy({{k: 'v2'}, {k: 'v1'}}, 'k')") shouldBe List(
      Map("k" -> "v1").asJava,
      Map("k" -> "v2").asJava,
    ).asJava

    evaluateAny("#COLLECTION.sortedAscBy({{k: 'v2'}, {k: 'v1'}}, #fieldName)", Map("fieldName" -> "k")) shouldBe List(
      Map("k" -> "v1").asJava,
      Map("k" -> "v2").asJava,
    ).asJava
  }

  test("reverse") {
    evaluateAny("#COLLECTION.reverse({'k1', 'k2'})") shouldBe List("k2", "k1").asJava
    evaluateAny("#COLLECTION.reverse({{k: 'v1'}, {k: 'v2'}})") shouldBe List(
      Map("k" -> "v2").asJava,
      Map("k" -> "v1").asJava,
    ).asJava
  }

  test("should throw if elements are not comparable") {
    val variables = Map(
      "list" -> List(new NonComparable).asJava,
      "map"  -> Map("key" -> new NonComparable).asJava,
    )

    // For `min` and `max` it can be validated at compilation stage
    Table(
      "expression",
      "#COLLECTION.min(#list)",
      "#COLLECTION.max(#list)",
    ).forEvery { expression =>
      val caught = intercept[SpelCompilationException] {
        evaluateType(expression, variables)
      }
      caught.getMessage should include(
        "NonComparable that does not match any of declared types (Comparable[Unknown]) when called with arguments (List[NonComparable]"
      )
    }

    // For other methods it is validated at runtime
    Table(
      "expression",
      "#COLLECTION.sortedAsc(#list)",
      "#COLLECTION.sortedDesc(#list)",
    ).forEvery { expression =>
      val caught = intercept[SpelExpressionEvaluationException] {
        evaluateAny(expression, variables)
      }
      caught.getMessage should include("Provided value is not comparable")
    }
  }

  test("should throw if elements are not mutually comparable") {
    val variables = Map(
      "list" -> List("a", 1).asJava,
      "map"  -> Map("k1" -> "a", "k2" -> 1).asJava,
    )
    Table(
      "expression",
      "#COLLECTION.min(#list)",
      "#COLLECTION.max(#list)",
      "#COLLECTION.sortedAsc(#list)",
      "#COLLECTION.sortedDesc(#list)",
    ).forEvery { expression =>
      val caught = intercept[SpelExpressionEvaluationException] {
        evaluateAny(expression, variables)
      }
      caught.message should include("cannot be cast to class")
    }
  }

  test("should throw if sorted records does not contain a required field") {
    Table(
      ("expression", "errorMessage"),
      ("#COLLECTION.sortedAscBy({{a: 'a'}, {a: 'b'}}, 'missing_field')", "Record must contain field: missing_field"),
      (
        "#COLLECTION.sortedAscBy({{a: 'a'}, {a: #NUMERIC.toNumber('42')}}, 'a')",
        "Field must implement Comparable interface"
      ),
    ).forEvery { case (expression, errorMessage) =>
      val caught = intercept[ParseException] {
        evaluateAny(expression)
      }
      caught.getMessage should include(errorMessage)
    }
  }

  private val types = Map(
    "unknownMap" -> Typed.fromDetailedType[java.util.Map[Any, Any]],
    "stringMap"  -> Typed.fromDetailedType[java.util.Map[String, Any]],
    "typedMap"   -> Typed.fromInstance(Map("key".asInstanceOf[Any] -> 20.asInstanceOf[Any]).asJava)
  )

  private class NonComparable {}

  private class CustomNumber extends Number {
    override def intValue(): Int = 1

    override def longValue(): Long = 2L

    override def floatValue(): Float = 3.0f

    override def doubleValue(): Double = 4.0
  }

}
