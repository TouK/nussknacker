package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{NothingTypeInfo, TypeInformation}
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, TraversableTypeInfo}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ProcessVersion, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.{ConfigGlobalParameters, NkGlobalParameters}
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.TypedScalaMapTypeInformation
import pl.touk.nussknacker.test.ClassLoaderWithServices

class TypeInformationDetectionSpec extends FunSuite with Matchers {

  private val loader = getClass.getClassLoader

  private def executionConfig(useTypingResultAware: Option[Boolean] = None) = new ExecutionConfig {
    setGlobalJobParameters(NkGlobalParameters("", ProcessVersion.empty, Some(ConfigGlobalParameters(None, None, useTypingResultAware, None)), None))
  }

  private def typeInformationForVariables(detection: TypeInformationDetection,
                                          ctx: ValidationContext): TypeInformation[ValidationContext] = {
    val tr = detection.forContext(ctx)
    tr.asInstanceOf[CaseClassTypeInfo[Context]].getTypeAt("variables")
  }

  test("Uses generic detection by default") {

    val detection = TypeInformationDetectionUtils.forExecutionConfig(executionConfig(), loader)
    val tr = typeInformationForVariables(detection, ValidationContext(Map("test1" -> Typed[String])))
    tr.isInstanceOf[TraversableTypeInfo[_, _]] shouldBe true
  }

  test("Uses TypingResultAware detection if configured") {

    val detection = TypeInformationDetectionUtils.forExecutionConfig(executionConfig(Some(true)), loader)
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
  override def customise(originalDetection: TypingResultAwareTypeInformationDetection): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
    case Unknown => new NothingTypeInfo
  }
}

class CustomTypeInformationDetection extends TypeInformationDetection {
  override def forInterpretationResult(validationContext: ValidationContext,
                                       output: Option[typing.TypingResult]): TypeInformation[InterpretationResult] = throw new IllegalArgumentException("Checking loader :)")

  override def forInterpretationResults(results: Map[String, ValidationContext]): TypeInformation[InterpretationResult] = throw new IllegalArgumentException("Checking loader :)")

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] = throw new IllegalArgumentException("Checking loader :)")

  override def forValueWithContext[T](validationContext: ValidationContext, value: typing.TypingResult): TypeInformation[ValueWithContext[T]] = throw new IllegalArgumentException("Checking loader :)")
}

