package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{NothingTypeInfo, TypeInformation}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfo
import pl.touk.nussknacker.engine.flink.api.typeinformation.{
  TypeInformationDetection,
  TypingResultAwareTypeInformationCustomisation
}
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.TypedScalaMapTypeInformation
import pl.touk.nussknacker.test.ClassLoaderWithServices

class TypeInformationDetectionSpec extends AnyFunSuite with Matchers {

  private val loader = getClass.getClassLoader

  private def typeInformationForVariables(
      detection: TypeInformationDetection,
      ctx: ValidationContext
  ): TypeInformation[ValidationContext] = {
    val tr = detection.forContext(ctx)
    tr.asInstanceOf[CaseClassTypeInfo[Context]].getTypeAt("variables")
  }

  test("Recognizes TypingResultAware customisation") {
    ClassLoaderWithServices.withCustomServices(
      List((classOf[TypingResultAwareTypeInformationCustomisation], classOf[CustomTypeInformationCustomisation])),
      loader
    ) { withServices =>
      val detection = TypingResultAwareTypeInformationDetection(withServices)
      typeInformationForVariables(detection, ValidationContext(Map("test1" -> Unknown)))
        .asInstanceOf[TypedScalaMapTypeInformation]
        .informations("test1") shouldBe new NothingTypeInfo
    }
  }

}

class CustomTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {

  override def customise(
      originalDetection: TypeInformationDetection
  ): PartialFunction[typing.TypingResult, TypeInformation[_]] = { case Unknown =>
    new NothingTypeInfo
  }

}

class CustomTypeInformationDetection extends TypeInformationDetection {

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] =
    throw new IllegalArgumentException("Checking loader :)")

  override def forValueWithContext[T](
      validationContext: ValidationContext,
      value: TypeInformation[T]
  ): TypeInformation[ValueWithContext[T]] = throw new IllegalArgumentException("Checking loader :)")

  override def forType[T](typingResult: typing.TypingResult): TypeInformation[T] = throw new IllegalArgumentException(
    "Checking loader :)"
  )

}
