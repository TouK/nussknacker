package pl.touk.nussknacker.engine.api.security

import org.apache.commons.lang3.ClassUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.exception.ExcludedPatternInvocationException

import java.io.File
import java.math.BigInteger
import scala.language.implicitConversions
import scala.util.matching.Regex

class SpelExpressionExcludeListSpec extends FunSuite with Matchers {

  val spelExpressionExcludeList: SpelExpressionExcludeList = SpelExpressionExcludeList.default
  val excludedPatterns: List[Regex] = spelExpressionExcludeList.excludedPatterns

  private def getClassFromName(className: String): Class[_] = {
    ClassUtils.getClass(className)
  }

  private def testRegexPattern(inputExpression: String,
                               testedPattern: Regex,
                               expectedResult: String) = {
    if (!excludedPatterns.map(excluded => excluded.regex).contains(testedPattern.regex))
      throw new NoSuchElementException("Pattern not found in ExcludeList")
    testedPattern.findFirstIn(inputExpression) shouldEqual Some(expectedResult)
  }

  test("Regex pattern matching test, element not found in list of excluded patterns") {

    val inputExpression = "org.springframework.expression.spel.standard.SpelExpressionParser"
    val testedPattern = "org.springframework".r
    val expectedResult = "org.springframework"
    a[NoSuchElementException] should be thrownBy {
      testRegexPattern(inputExpression, testedPattern, expectedResult)
    }
  }

  test("Regex pattern matching test, org.springframework") {
    val inputExpression = "org.springframework.expression.spel.standard.SpelExpressionParser"
    val testedPattern = "org\\.springframework".r
    val expectedResult = "org.springframework"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.System") {
    val inputExpression = "java.lang.System.exit()"
    val testedPattern = "java\\.lang\\.System".r
    val expectedResult = "java.lang.System"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.Thread") {
    val inputExpression = "java.lang.Thread.currentThread()"
    val testedPattern = "java\\.lang\\.Thread".r
    val expectedResult = "java.lang.Thread"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.Runtime") {
    val inputExpression = "java.lang.Runtime.getRuntime()"
    val testedPattern = "java\\.lang\\.Runtime".r
    val expectedResult = "java.lang.Runtime"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.ProcessBuilder") {
    val inputExpression = "new java.lang.ProcessBuilder()"
    val testedPattern = "java\\.lang\\.ProcessBuilder".r
    val expectedResult = "java.lang.ProcessBuilder"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.invoke") {
    val inputExpression = "java.lang.invoke.MethodHandles.lookup()"
    val testedPattern = "java\\.lang\\.invoke".r
    val expectedResult = "java.lang.invoke"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.lang.reflect") {
    val inputExpression = "java.lang.reflect.Method.classModifiers()"
    val testedPattern = "java\\.lang\\.reflect".r
    val expectedResult = "java.lang.reflect"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.net") {
    val inputExpression = "java.net.URLConnection.getURL()"
    val testedPattern = "java\\.net".r
    val expectedResult = "java.net"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.io") {
    val inputExpression = "java.io.File.listRoots()"
    val testedPattern = "java\\.io".r
    val expectedResult = "java.io"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, java.nio") {
    val inputExpression = "java.nio.DoubleBuffer.allocate(1024)"
    val testedPattern = "java\\.nio".r
    val expectedResult = "java.nio"
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Regex pattern matching test, exec") {
    val inputExpression = "Runtime.getRuntime().exec("
    val testedPattern = "exec\\(".r
    val expectedResult = "exec("
    testRegexPattern(inputExpression, testedPattern, expectedResult)
  }

  test("Blocking usage of unallowed package java.io, executed on object") {
    a[ExcludedPatternInvocationException] should be thrownBy {
      spelExpressionExcludeList.blockExcluded(new File("test"), "canRead()")
    }
  }

  test("Blocking usage of unallowed package java.net, executed on class") {
    a[ExcludedPatternInvocationException] should be thrownBy {
      val testClass = getClassFromName("java.net.URLConnection")
      spelExpressionExcludeList.blockExcluded(testClass, "getURL()")
    }
  }

  test("Blocking usage of class Thread, executed on object") {
    a[ExcludedPatternInvocationException] should be thrownBy {
      spelExpressionExcludeList.blockExcluded(new Thread(), "run()")
    }
  }

  test("Blocking usage of class java.lang.System, executed on class") {
    a[ExcludedPatternInvocationException] should be thrownBy {
      val testClass: Class[_] = getClassFromName("java.lang.System")
      spelExpressionExcludeList.blockExcluded(testClass, "exit()")
    }
  }

  test("Blocking usage of class java.lang.ProcessBuilder, executed on instance") {
    a[ExcludedPatternInvocationException] should be thrownBy {
      spelExpressionExcludeList.blockExcluded(new ProcessBuilder("help"), "start()")
    }
  }

  test("Unblocked class String, executed on object") {
    spelExpressionExcludeList.blockExcluded(new String(), "valueOf(1)")
  }

  test("Unblocked class BigInteger, executed on class") {
    spelExpressionExcludeList.blockExcluded(BigInteger.ONE, "longValue()")
  }

}
