package pl.touk.nussknacker.engine.types

import java.util.regex.Pattern

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ClassMemberPatternPredicate}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef

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

  case class SampleClass(foo: Int, bar: String) extends SampleAbstractClass with SampleInterface

  it should "extract public fields from scala case class, and java class" in {
    val testCases = Table(("class", "className"),
      (classOf[SampleClass], "SampleClass"),
      (classOf[JavaSampleClass], "JavaSampleClass")
    )

    forAll(testCases) { (clazz, clazzName) =>
      val infos = EspTypeUtils.clazzAndItsChildrenDefinition(clazz)(ClassExtractionSettings.Default)
      val sampleClassInfo = infos.find(_.clazzName.refClazzName.contains(clazzName)).get

      sampleClassInfo.methods shouldBe Map(
        "foo" -> ClazzRef("int"),
        "bar" -> ClazzRef("java.lang.String")
      )
    }
  }

  it should "skip blacklisted properties" in {
    val testCasses = Table(("class", "className"),
      (classOf[SampleClass], "SampleClass"),
      (classOf[JavaSampleClass], "JavaSampleClass")
    )

    val testClassPatterns = Table("classPattern",
      ".*SampleClass",
      ".*SampleAbstractClass",
      ".*SampleInterface"
    )

    forAll(testCasses) { (clazz, clazzName) =>
      forAll(testClassPatterns) { classPattern =>
        val infos = EspTypeUtils.clazzAndItsChildrenDefinition(clazz)(ClassExtractionSettings(Seq(
          ClassMemberPatternPredicate(Pattern.compile(classPattern), Pattern.compile("ba.*"))
        )))
        val sampleClassInfo = infos.find(_.clazzName.refClazzName.contains(clazzName)).get

        sampleClassInfo.methods shouldBe Map(
          "foo" -> ClazzRef("int")
        )
      }
    }
  }


}
