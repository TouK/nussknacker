package pl.touk.nussknacker.test

import com.networknt.schema.{InputFormat, JsonSchema}
import io.circe.Json
import org.scalactic.source
import org.scalatest.{Assertion, Assertions}
import org.scalatest.matchers.{Matcher, MatchResult}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

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

  class ValidateJsonMatcher(json: Json) extends Matcher[JsonSchema] {

    override def apply(left: JsonSchema): MatchResult = {
      val errors = left.validate(json.spaces2, InputFormat.JSON).asScala
      MatchResult(
        errors.isEmpty,
        errors
          .map(_.getMessage)
          .mkString(s"Validation of given JSON\n$json against given schema returned errors:\n", ",\n", ""),
        s"Validation of given JSON\n$json against given schema returned no errors."
      )
    }

  }

  def validateJson(json: Json): Matcher[JsonSchema] = new ValidateJsonMatcher(json)

}
