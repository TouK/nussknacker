package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.util.Implicits.RichStringList

import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

/**
 * This is trait that allows for providing more details TypeInformation when ValidationContext is known,
 * by default generic Flink mechanisms are used
 */
trait TypeInformationDetection extends Serializable {

  def forContext(validationContext: ValidationContext): TypeInformation[Context]

  def forValueWithContext[T](
      validationContext: ValidationContext,
      value: TypingResult
  ): TypeInformation[ValueWithContext[T]] =
    forValueWithContext(validationContext, forType(value).asInstanceOf[TypeInformation[T]])

  def forValueWithContext[T](
      validationContext: ValidationContext,
      value: TypeInformation[T]
  ): TypeInformation[ValueWithContext[T]]

  final def forType[T](typingResult: TypingResult): TypeInformation[T] = forTypeWithDetails(
    typingResult
  ).typeInformation

  def forTypeWithDetails[T](typingResult: TypingResult): TypeInformationWithDetails[T]

}

final case class TypeInformationWithDetails[T](typeInformation: TypeInformation[T], isTableApiCompatible: Boolean) {

  def map[R](f: TypeInformation[T] => TypeInformation[R]): TypeInformationWithDetails[R] =
    copy(typeInformation = f(typeInformation))

}

object TypeInformationWithDetails {

  def combine[A, B, R](first: TypeInformationWithDetails[A], sec: TypeInformationWithDetails[B])(
      f: (TypeInformation[A], TypeInformation[B]) => TypeInformation[R]
  ): TypeInformationWithDetails[R] = {
    TypeInformationWithDetails(
      f(first.typeInformation, sec.typeInformation),
      isTableApiCompatible = first.isTableApiCompatible && sec.isTableApiCompatible
    )
  }

  implicit class TypeInformationExtension[T](typeInformation: TypeInformation[T]) {

    def toTableApiCompatibleTypeInformation: TypeInformationWithDetails[T] =
      TypeInformationWithDetails(typeInformation, isTableApiCompatible = true)

    def toTableApiIncompatibleTypeInformation: TypeInformationWithDetails[T] =
      TypeInformationWithDetails(typeInformation, isTableApiCompatible = false)

  }

}

object TypeInformationDetection {

  // We use SPI to provide implementation of TypeInformationDetection because we don't want to make
  // implementation classes available in flink-components-api module.
  val instance: TypeInformationDetection = {
    val classloader = Thread.currentThread().getContextClassLoader
    ServiceLoader
      .load(classOf[TypeInformationDetection], classloader)
      .asScala
      .toList match {
      case only :: Nil => only
      case Nil =>
        throw new IllegalStateException(
          s"Missing ${classOf[TypeInformationDetection].getSimpleName} implementation on the classpath. " +
            s"Classloader: ${printClassloaderDebugDetails(classloader)}. " +
            s"Ensure that your classpath is correctly configured, flinkExecutor.jar is probably missing"
        )
      case moreThanOne =>
        throw new IllegalStateException(
          s"More than one ${classOf[TypeInformationDetection].getSimpleName} implementations on the classpath: $moreThanOne. " +
            s"Classloader: ${printClassloaderDebugDetails(classloader)}"
        )
    }
  }

  private def printClassloaderDebugDetails(classLoader: ClassLoader) = classLoader match {
    case urlCL: URLClassLoader =>
      s"${urlCL.getURLs.map(_.toString).toList.mkCommaSeparatedStringWithPotentialEllipsis(10)})"
    case other =>
      s"${other.getName}"
  }

}
