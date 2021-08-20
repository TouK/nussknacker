package pl.touk.nussknacker.engine.api.security

import org.apache.commons.lang3.ClassUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.SpelExpressionBlacklist
import pl.touk.nussknacker.engine.api.exception.BlacklistedPatternInvocationException

import java.io.File
import java.math.BigInteger
import scala.language.implicitConversions
import scala.util.matching.Regex

class SpelExpressionBlacklistSpec extends FunSuite with Matchers {

  val spelExpressionBlacklist: SpelExpressionBlacklist = SpelExpressionBlacklist.default
  val blacklistedPatterns: Map[String, Regex] = spelExpressionBlacklist.blacklistedPatterns

  private def getClassFromName(className: String): Class[_] = {
    ClassUtils.getClass(className)
  }

  test("Regex pattern matching test, org.springframework") {
    val inputExpression = "org.springframework.expression.spel.standard.SpelExpressionParser"
    blacklistedPatterns("spring").findFirstIn(inputExpression) shouldEqual Some("org.springframework")
  }

  test("Regex pattern matching test, java.lang.System") {
    val inputExpression = "java.lang.System.exit()"
    blacklistedPatterns("system").findFirstIn(inputExpression) shouldEqual Some("java.lang.System")
  }

  test("Regex pattern matching test, java.lang.Thread") {
    val inputExpression = "java.lang.Thread.currentThread()"
    blacklistedPatterns("thread").findFirstIn(inputExpression) shouldEqual Some("java.lang.Thread")
  }

  test("Regex pattern matching test, java.lang.Runtime") {
    val inputExpression = "java.lang.Runtime.getRuntime()"
    blacklistedPatterns("runtime").findFirstIn(inputExpression) shouldEqual Some("java.lang.Runtime")
  }

  test("Regex pattern matching test, java.lang.ProcessBuilder") {
    val inputExpression = "new java.lang.ProcessBuilder()"
    blacklistedPatterns("processBuilder").findFirstIn(inputExpression) shouldEqual Some("java.lang.ProcessBuilder")
  }

  test("Regex pattern matching test, java.lang.invoke") {
    val inputExpression = "java.lang.invoke.MethodHandles.lookup()"
    blacklistedPatterns("invoke").findFirstIn(inputExpression) shouldEqual Some("java.lang.invoke")
  }

  test("Regex pattern matching test, java.lang.reflect") {
    val inputExpression = "java.lang.reflect.Method.classModifiers()"
    blacklistedPatterns("reflect").findFirstIn(inputExpression) shouldEqual Some("java.lang.reflect")
  }

  test("Regex pattern matching test, java.net") {
    val inputExpression = "java.net.URLConnection.getURL()"
    blacklistedPatterns("net").findFirstIn(inputExpression) shouldEqual Some("java.net")
  }

  test("Regex pattern matching test, java.io") {
    val inputExpression = "java.io.File.listRoots()"
    blacklistedPatterns("io").findFirstIn(inputExpression) shouldEqual Some("java.io")
  }

  test("Regex pattern matching test, java.nio") {
    val inputExpression = "java.nio.DoubleBuffer.allocate(1024)"
    blacklistedPatterns("nio").findFirstIn(inputExpression) shouldEqual Some("java.nio")
  }

  test("Regex pattern matching test, exec") {
    val inputExpression = "Runtime.getRuntime().exec("
    blacklistedPatterns("exec").findFirstIn(inputExpression) shouldEqual Some("exec(")
  }

  test("Blocking usage of unallowed package java.io, executed on object") {
    a[BlacklistedPatternInvocationException] should be thrownBy {
      spelExpressionBlacklist.blockBlacklisted(new File("test"), "canRead()")
    }
  }

  test("Blocking usage of unallowed package java.net, executed on class") {
    a[BlacklistedPatternInvocationException] should be thrownBy {
      val testClass = getClassFromName("java.net.URLConnection")
      spelExpressionBlacklist.blockBlacklisted(testClass, "getURL()")
    }
  }

  test("Blocking usage of class Thread, executed on object") {
    a[BlacklistedPatternInvocationException] should be thrownBy {
      spelExpressionBlacklist.blockBlacklisted(new Thread(), "run()")
    }
  }

  test("Blocking usage of class java.lang.System, executed on class") {
    a[BlacklistedPatternInvocationException] should be thrownBy {
      val testClass: Class[_] = getClassFromName("java.lang.System")
      spelExpressionBlacklist.blockBlacklisted(testClass, "exit()")
    }
  }

  test("Blocking usage of class java.lang.ProcessBuilder, executed on instance") {
    a[BlacklistedPatternInvocationException] should be thrownBy {
      spelExpressionBlacklist.blockBlacklisted(new ProcessBuilder("help"), "start()")
    }
  }

  test("Unblocked class String, executed on object") {
    spelExpressionBlacklist.blockBlacklisted(new String(), "valueOf(1)")
  }

  test("Unblocked class BigInteger, executed on class") {
    spelExpressionBlacklist.blockBlacklisted(BigInteger.ONE, "longValue()")
  }

}
