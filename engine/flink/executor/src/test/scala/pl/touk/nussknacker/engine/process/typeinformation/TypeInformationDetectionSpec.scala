package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{NothingTypeInfo, TypeInformation}
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, TraversableTypeInfo}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{Context, ProcessVersion, ValueWithContext}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.api.{ConfigGlobalParameters, NkGlobalParameters}
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.TypedScalaMapTypeInformation
import pl.touk.nussknacker.test.ClassLoaderWithServices

class TypeInformationDetectionSpec extends AnyFunSuite with Matchers {

  private val loader = getClass.getClassLoader

  private def executionConfig(useTypingResultAware: Option[Boolean] = None) = new ExecutionConfig {
    setGlobalJobParameters(NkGlobalParameters("",
      ProcessVersion.empty, Some(ConfigGlobalParameters(None, useTypingResultAware, None, None)), None, Map.empty))
  }

  private def typeInformationForVariables(detection: TypeInformationDetection,
                                          ctx: ValidationContext): TypeInformation[ValidationContext] = {
    val tr = detection.forContext(ctx)
    tr.asInstanceOf[CaseClassTypeInfo[Context]].getTypeAt("variables")
  }

  ignore("Uses generic detection if TypingResultAware turned off") {

    val detection = TypeInformationDetectionUtils.forExecutionConfig(executionConfig(Some(false)), loader)
    val tr = typeInformationForVariables(detection, ValidationContext(Map("test1" -> Typed[String])))
    tr.isInstanceOf[TraversableTypeInfo[_, _]] shouldBe true
  }

  test("Uses TypingResultAware by default") {

    val detection = TypeInformationDetectionUtils.forExecutionConfig(executionConfig(), loader)
    typeInformationForVariables(detection, ValidationContext(Map("test1" -> Typed[String])))
      .asInstanceOf[TypedScalaMapTypeInformation].informations("test1") shouldBe TypeInformation.of(classOf[String])
  }

  test("Recognizes TypingResultAware customisation") {
    val ec = executionConfig(Some(true))

    ClassLoaderWithServices.withCustomServices(List(
      (classOf[TypingResultAwareTypeInformationCustomisation], classOf[CustomTypeInformationCustomisation])), loader) { withServices =>
      val detection = TypeInformationDetectionUtils.forExecutionConfig(ec, withServices)
      typeInformationForVariables(detection, ValidationContext(Map("test1" -> Unknown)))
        .asInstanceOf[TypedScalaMapTypeInformation].informations("test1") shouldBe new NothingTypeInfo
    }
  }

  test("Uses custom TypeInformationDetection if detected") {
    ClassLoaderWithServices.withCustomServices(List(
      (classOf[TypeInformationDetection], classOf[CustomTypeInformationDetection])), loader) { withServices =>
      val detection = TypeInformationDetectionUtils.forExecutionConfig(executionConfig(Some(true)), withServices)

      intercept[IllegalArgumentException] {
        detection.forContext(ValidationContext())
      }.getMessage shouldBe "Checking loader :)"

    }

  }
}


class CustomTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {
  override def customise(originalDetection: TypeInformationDetection): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
    case Unknown => new NothingTypeInfo
  }
}

class CustomTypeInformationDetection extends TypeInformationDetection {

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] = throw new IllegalArgumentException("Checking loader :)")

  override def forValueWithContext[T](validationContext: ValidationContext, value: typing.TypingResult): TypeInformation[ValueWithContext[T]] = throw new IllegalArgumentException("Checking loader :)")

  override def forType(typingResult: typing.TypingResult): TypeInformation[AnyRef] = throw new IllegalArgumentException("Checking loader :)")
}

