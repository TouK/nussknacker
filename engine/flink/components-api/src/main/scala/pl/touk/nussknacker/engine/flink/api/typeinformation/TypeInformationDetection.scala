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

  def forType[T](typingResult: TypingResult): TypeInformation[T]

}

object TypeInformationDetection {

  // We could just inline implementation of TypeInformationDetection but we don't want expose too much implementation
  // details so we load the implementation using SPI. As an alternative to SPI was to use Class.forName
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
            s"Ensure that your classpath is correctly configured, probable flinkExecutor.jar is missing"
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
