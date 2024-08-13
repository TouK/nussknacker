package pl.touk.nussknacker.test

import cats.Show
import cats.data.Validated
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.restassured.response.ValidatableResponse
import org.hamcrest.{BaseMatcher, Description, Matcher}
import pl.touk.nussknacker.test.NuRestAssureMatchers.{EqualsJson, MatchJsonWithRegexValues, MatchNdJsonWithRegexValues}
import ujson._

import scala.util.Try

trait NuRestAssureMatchers {

  object regexes {
    val localDateRegex     = "^\\\\d{4}-\\\\d{2}-\\\\d{2}\\\\s\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{1,3}$$"
    val localDateTimeRegex = "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{1,3}$$"
    val zuluDateRegex      = "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}(?:\\\\.\\\\d{3,6})?Z$$"
    // ujson treats all numbers as a double
    val digitsRegex  = "^\\\\d+\\\\.0$$"
    val decimalRegex = "^\\\\d+(\\\\.\\\\d+)?([eE]\\\\d+)?$$"
  }

  def equalsJson(expectedJsonString: String): Matcher[ValidatableResponse] =
    new EqualsJson(expectedJsonString)

  def matchJsonWithRegexValues(expectedJsonWithRegexValuesString: String): Matcher[ValidatableResponse] =
    new MatchJsonWithRegexValues(expectedJsonWithRegexValuesString)

  def matchAllNdJsonWithRegexValues(expectedJsonWithRegexValuesString: String): Matcher[ValidatableResponse] =
    new MatchNdJsonWithRegexValues(expectedJsonWithRegexValuesString)
}

object NuRestAssureMatchers extends NuRestAssureMatchers {

  private type JSON = ujson.Value.Value

  private class EqualsJson(expectedJsonString: String) extends BaseMatcher[ValidatableResponse] {

    private val expectedJson = ujson.read(expectedJsonString)

    override def matches(actual: Any): Boolean = {
      actual match {
        case str: String => ujson.read(str) == expectedJson
        case _           => false
      }
    }

    override def describeTo(description: Description): Unit = {
      description.appendValue(expectedJsonString)
    }

  }

  private class MatchJsonWithRegexValues(expectedJsonWithRegexValuesString: String)
      extends BaseMatcher[ValidatableResponse]
      with LazyLogging {

    private val expectedJson = ujson.read(expectedJsonWithRegexValuesString)

    override def matches(actual: Any): Boolean = {
      actual match {
        case str: String => check(ujson.read(str))
        case _           => false
      }
    }

    override def describeTo(description: Description): Unit =
      description.appendValue(expectedJsonWithRegexValuesString)

    private def check(json: JSON) = {
      compareJsons(json, expectedJson, JsonValueParent.Root) match {
        case Validated.Invalid(msg) =>
          logger.error(s"Comparing JSONs errors:\n$msg")
          false
        case Validated.Valid(()) =>
          true
      }
    }

    private def compareJsons(
        currentJsonCursor: JSON,
        expectedJsonCursor: JSON,
        parent: JsonValueParent
    ): Validated[String, Unit] = {
      (currentJsonCursor, expectedJsonCursor) match {
        case (Str(s1), Str(s2)) =>
          if (s1 == s2) Validated.Valid(())
          else compareAssumingThatExpectedStringCouldBeARegex(s1, s2, parent)
        case (Num(n1), Num(n2)) =>
          if (n1 == n2) Validated.Valid(())
          else Validated.Invalid(errorString(s"$n1", s"$n2", parent))
        case (Num(n1), Str(s2)) =>
          compareAssumingThatExpectedStringCouldBeARegex(s"$n1", s2, parent)
        case (Bool(b1), Bool(b2)) =>
          if (b1 == b2) Validated.Valid(())
          else Validated.Invalid(errorString(s"$b1", s"$b2", parent))
        case (Bool(b1), Str(s2)) =>
          compareAssumingThatExpectedStringCouldBeARegex(s"$b1", s2, parent)
        case (Null, Null) =>
          Validated.Valid(())
        case (Arr(a1), Arr(a2)) =>
          compareArrays(a1, a2, parent)
        case (Obj(o1), Obj(o2)) =>
          compareObjects(o1, o2, parent)
        case (json1, json2) =>
          Validated.Invalid(errorString(s"$json1", s"$json2", parent))
      }
    }

    private def compareArrays(a1: Arr, a2: Arr, parent: JsonValueParent) = {
      if (a1 == a2) {
        Validated.Valid(())
      } else if (a1.value.size == a2.value.size) {
        a1.value
          .zip(a2.value)
          .zipWithIndex
          .foldLeft(List.empty[Validated[String, Unit]]) { case (validationResults, ((elemA1, elemA2), idx)) =>
            compareJsons(elemA1, elemA2, JsonValueParent.Array(parent, idx)) :: validationResults
          }
          .reverse
          .sequence
          .map(_ => ())
      } else {
        Validated.Invalid(errorString(s"$a1", s"$a2", parent))
      }
    }

    private def compareObjects(o1: Obj, o2: Obj, parent: JsonValueParent) = {
      val o1Keys = o1.value.keys.toList.sorted
      val o2Keys = o2.value.keys.toList.sorted
      if (o1Keys == o2Keys) {
        o1Keys
          .foldLeft(List.empty[Validated[String, Unit]]) { case (validationResults, key) =>
            compareJsons(
              o1.value.apply(key),
              o2.value.apply(key),
              JsonValueParent.Field(parent, key)
            ) :: validationResults
          }
          .reverse
          .sequence
          .map(_ => ())
      } else {
        Validated.Invalid(errorString(s"$o1", s"$o2", parent))
      }
    }

    private def compareAssumingThatExpectedStringCouldBeARegex(
        currentValueString: String,
        expectedValueRegex: String,
        source: JsonValueParent
    ): Validated[String, Unit] = {
      stringToRegex(expectedValueRegex).map { regex =>
        currentValueString match {
          case regex()   => Validated.Valid(())
          case regex(_*) => Validated.Valid(())
          case _ => Validated.Invalid(errorString(currentValueString, expectedValueRegex, source, withRegex = true))
        }
      }.get
    }

    private def errorString(current: String, expected: String, source: JsonValueParent, withRegex: Boolean = false) = {
      s"""
         |* Comparing JSONs failed at ${source.show}
         |  Current value:
         |  $current
         |  ${if (withRegex) "should match" else "expected value"}:
         |  $expected
         |""".stripMargin
    }

    private def stringToRegex(str: String) = Try(str.r)

    private sealed trait JsonValueParent

    private object JsonValueParent {
      case object Root                                               extends JsonValueParent
      sealed case class Field(parent: JsonValueParent, name: String) extends JsonValueParent
      sealed case class Array(parent: JsonValueParent, index: Int)   extends JsonValueParent

      implicit val show: Show[JsonValueParent] = Show.show(showParent)

      private def showParent(parent: JsonValueParent) = parent match {
        case JsonValueParent.Root                     => "$"
        case JsonValueParent.Field(parent, fieldName) => s"${parent.show}.$fieldName"
        case JsonValueParent.Array(parent, index)     => s"${parent.show}[$index]"
      }

    }

  }

  private class MatchNdJsonWithRegexValues(expectedJsonWithRegexValuesString: String)
      extends MatchJsonWithRegexValues(expectedJsonWithRegexValuesString) {

    override def matches(actual: Any): Boolean = {
      actual match {
        case str: String => str.split('\n').forall(super.matches)
        case _           => false
      }
    }

  }

}
