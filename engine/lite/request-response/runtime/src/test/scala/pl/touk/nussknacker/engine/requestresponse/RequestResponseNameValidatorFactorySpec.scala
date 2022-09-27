package pl.touk.nussknacker.engine.requestresponse

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{MetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class RequestResponseNameValidatorFactorySpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("validates name") {
    val validator = new RequestResponseNameValidatorFactory().validator(ConfigFactory.empty())

    val testTypes = Table(("slug", "isValid"),
      ("blah", true),
      ("1blah", false),
      ("1bl-ah", false),
      ("1bl-ah1", false),
      ("-bl-ah", false),
      ("bl-ah", true),
      ("1bl-ah", false),
      (s"bl${(1 to 70).map(_ => "a").mkString}h", false)
    )
    forAll(testTypes) { case (slug, isValid) =>
      val scenario = CanonicalProcess(MetaData("name", RequestResponseMetaData(Some(slug))), Nil)
      validator.validate(scenario).isValid shouldBe isValid
    }
  }

}
