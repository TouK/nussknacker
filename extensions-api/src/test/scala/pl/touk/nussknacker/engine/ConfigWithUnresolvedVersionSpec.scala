package pl.touk.nussknacker.engine

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConfigWithUnresolvedVersionSpec extends AnyFunSuite with Matchers {

  private val (someEnvVariableName, someEnvVariableValue) = sys.env.get("HOME").map("HOME" -> _).getOrElse(sys.env.head)

  test("should contain both resolved and with unresolved env variables") {
    val config = ConfigFactory.parseString(s"fromEnv: $${$someEnvVariableName}")
    val result = ConfigWithUnresolvedVersion(config)

    result.resolved.getString("fromEnv") shouldEqual someEnvVariableValue
    result.withUnresolvedEnvVariables.root().get("fromEnv").render() shouldEqual s"$${$someEnvVariableName}"
  }

  test("should be able to substitute variables with references to root values") {
    val config = ConfigFactory.parseString(
      s"""rootValue: xyz
         |nested {
         |  substitution: $${rootValue}
         |  fromEnv: $${$someEnvVariableName}
         |}""".stripMargin)
    val result = ConfigWithUnresolvedVersion(config)
    val nested = result.getConfig("nested")

    nested.resolved.getString("fromEnv") shouldEqual someEnvVariableValue
    nested.resolved.getString("substitution") shouldEqual "xyz"
    nested.withUnresolvedEnvVariables.root().get("fromEnv").render() shouldEqual s"$${$someEnvVariableName}"
    nested.withUnresolvedEnvVariables.getString("substitution") shouldEqual "xyz"
  }

  test("unresolved version not preserve optional, not resolvable references") {
    val config = ConfigFactory.parseString(s"fromEnv: $${?NOT_EXISTING_ENV_VARIABLE}")
    val result = ConfigWithUnresolvedVersion(config)

    result.resolved.hasPath("fromEnv") shouldBe false
    // Unfortunately Config.resolve() always resolve optional, not existing variables to null
    // Because for withUnresolvedEnvVariables we don't use env variables, some properties will be removed.
    // On the other hand we can't left this variable unresolved at all because we need to have access to root level
    // references for substitution purpose (see the test above)
    result.withUnresolvedEnvVariables.hasPath("fromEnv") shouldBe false
  }

}
