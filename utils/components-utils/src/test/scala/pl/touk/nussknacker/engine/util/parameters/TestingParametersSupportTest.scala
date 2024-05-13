package pl.touk.nussknacker.engine.util.parameters

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.parameter.ParameterName

class TestingParametersSupportTest extends AnyFunSuite with Matchers {

  test("should unflatten flat params") {
    val flatParams = Map[ParameterName, AnyRef](
      ParameterName("a") -> (42: java.lang.Integer),
      ParameterName("b") -> "str",
    )

    val unflattenedParams = TestingParametersSupport.unflattenParameters(flatParams)

    unflattenedParams shouldBe flatParams.map { case (ParameterName(name), value) => name -> value }
  }

  test("should unflatten params with delimiter") {
    val flatParams = Map[ParameterName, AnyRef](
      ParameterName("a")     -> (42: java.lang.Integer),
      ParameterName("b.x")   -> (true: java.lang.Boolean),
      ParameterName("b.y")   -> (false: java.lang.Boolean),
      ParameterName("c.x.x") -> "str1",
      ParameterName("c.x.y") -> "str2",
      ParameterName("c.x.z") -> "str3",
    )

    val unflattenedParams = TestingParametersSupport.unflattenParameters(flatParams)

    unflattenedParams shouldBe Map(
      "a" -> 42,
      "b" -> Map(
        "x" -> true,
        "y" -> false,
      ),
      "c" -> Map(
        "x" -> Map(
          "x" -> "str1",
          "y" -> "str2",
          "z" -> "str3",
        )
      ),
    )
  }

}
