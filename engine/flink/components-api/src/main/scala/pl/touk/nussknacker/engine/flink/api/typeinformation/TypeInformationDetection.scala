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
sealed trait TypeInformationDetection extends Serializable {

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

// We use DefaultTypeInformationDetection SPI to provide a implementation of TypeInformationDetection because we don't
// want to make implementation classes available in flink-components-api module.
trait DefaultTypeInformationDetection extends TypeInformationDetection
// We use CustomTypeInformationDetection SPI to provide a compatibility layer for Flink API
trait CustomTypeInformationDetection extends TypeInformationDetection

object TypeInformationDetection {

  val instance: TypeInformationDetection = {
    val classloader = Thread.currentThread().getContextClassLoader
    def defaultTypeInfoDetection() = ServiceLoader
      .load(classOf[DefaultTypeInformationDetection], classloader)
      .asScala
      .headOption
    val customTypeInfoDetections = ServiceLoader
      .load(classOf[CustomTypeInformationDetection], classloader)
      .asScala
      .toList

    customTypeInfoDetections match {
      case Nil =>
        defaultTypeInfoDetection() match {
          case Some(default) => default
          case None =>
            throw new IllegalStateException(
              s"Missing ${classOf[TypeInformationDetection].getSimpleName} implementation on the classpath. " +
                s"Classloader: ${printClassloaderDebugDetails(classloader)}. " +
                s"Ensure that your classpath is correctly configured, flinkExecutor.jar is probably missing"
            )
        }
      case custom :: Nil => custom
      case mutlipleCustomizations =>
        throw new IllegalStateException(
          s"More than one ${classOf[CustomTypeInformationDetection].getSimpleName} implementations on the" +
            s" classpath: $mutlipleCustomizations. Classloader: ${printClassloaderDebugDetails(classloader)}"
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
