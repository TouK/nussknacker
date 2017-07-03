package pl.touk.esp.engine.types

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._

class EspTypeUtilsSpec extends FlatSpec with Matchers {

  val signatures = Table(("signature", "value", "matches"),
    (java.lang.Boolean.TYPE, classOf[java.lang.Boolean], true),
    (java.lang.Long.TYPE, classOf[java.lang.Long], true),
    (java.lang.Integer.TYPE, classOf[java.lang.Integer], true),
    (classOf[java.lang.Long], classOf[java.lang.Integer], true),
    (classOf[java.lang.Long], java.lang.Integer.TYPE, true),
    (java.lang.Long.TYPE, java.lang.Integer.TYPE, true),
    (java.lang.Long.TYPE, classOf[java.lang.Integer], true),
    (java.lang.Long.TYPE, classOf[java.lang.Integer], true),

    (java.lang.Character.TYPE, classOf[java.lang.Character], true),
    (classOf[java.lang.Number], classOf[java.lang.Integer], true),
    (java.lang.Integer.TYPE, classOf[java.lang.Long], false)
  )

  it should "check if signature is possible" in {

    forAll(signatures) { (signature, value, matches) =>
      EspTypeUtils.signatureElementMatches(signature, value) shouldBe matches
    }

  }

}
