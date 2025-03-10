package pl.touk.nussknacker.k8s.manager

import org.scalatest.Inspectors.forAll
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table

class K8sUtilsTest extends AnyFunSuiteLike with Matchers {

  test("should sanitize illegal characters in the middle") {
    K8sUtils.sanitizeObjectName("foo$!?a") shouldBe "foo-a"
  }

  test("should sanitize illegal characters at the beginning and at the end") {
    K8sUtils.sanitizeObjectName("foo-") shouldBe "foo-x"
    K8sUtils.sanitizeObjectName("foo???") shouldBe "foo-x"
    K8sUtils.sanitizeObjectName("-foo") shouldBe "x-foo"
    K8sUtils.sanitizeObjectName("???foo") shouldBe "x-foo"
  }

  test("should truncate name") {
    forAll(Table("input", 0.to(100).map(_ => "a").mkString, 0.to(100).map(_ => "-").mkString)) { input =>
      val afterTruncation = K8sUtils.sanitizeObjectName(input)
      afterTruncation should have length K8sUtils.maxObjectNameLength
      afterTruncation.head.isLetterOrDigit shouldBe true
      afterTruncation.last.isLetterOrDigit shouldBe true
    }
  }

  test("shouldn't sanitize last character if some text is appended") {
    K8sUtils.sanitizeObjectName("foo-", "append") shouldBe "foo-append"
  }

}
