package pl.touk.nussknacker.test

import org.everit.json.schema.Schema
import org.json.JSONObject
import org.scalactic.source
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{Assertion, Assertions}

import scala.reflect.ClassTag
import scala.util.{Success, Try}

trait NuScalaTestAssertions extends Assertions {

  def assertThrowsWithParent[T <: AnyRef](
      f: => Any
  )(implicit classTag: ClassTag[T], pos: source.Position): Assertion = {
    assertThrows[T] {
      try {
        f
      } catch {
        case u: Throwable if u.getCause != null =>
          throw u.getCause
      }
    }
  }

  class ValidateJsonMatcher(json: JSONObject) extends Matcher[Schema] {

    override def apply(left: Schema): MatchResult =
      MatchResult(
        Try(left.validate(json)) == Success(()),
        s"JSON $json cannot be validated by schema $left",
        s"JSON $json can be validated by schema $left"
      )

  }

  def validateJson(json: JSONObject): Matcher[Schema] = new ValidateJsonMatcher(json)

}
