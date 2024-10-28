package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.util.Implicits.RichStringList

import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.reflect.{ClassTag, classTag}

/**
 * This is trait that allows for providing more details TypeInformation when ValidationContext is known,
 * by default generic Flink mechanisms are used
 */
trait TypeInformationDetection extends Serializable {

  // Flink doesn't have any special null serializer, so we use String TypeInfo
  def forNull[T]: TypeInformation[T] = forType[T](Typed.typedClass[String])

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

  def forClass[T: ClassTag]: TypeInformation[T] = {
    val klass = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    forClass(klass)
  }

  def forClass[T](klass: Class[T]): TypeInformation[T] =
    forType[T](Typed.typedClass(klass))

  def forType[T](typingResult: TypingResult): TypeInformation[T]

  def priority: Int

}

object TypeInformationDetection {

  // We use SPI to provide implementation of TypeInformationDetection because we don't want to make
  // implementation classes available in flink-components-api module.
  val instance: TypeInformationDetection = {
    FlinkBaseTypeInfoRegister.makeSureBaseTypesAreRegistered()

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
      case moreThanOne => moreThanOne.maxBy(_.priority)
    }
  }

  private def printClassloaderDebugDetails(classLoader: ClassLoader) = classLoader match {
    case urlCL: URLClassLoader =>
      s"${urlCL.getURLs.map(_.toString).toList.mkCommaSeparatedStringWithPotentialEllipsis(10)})"
    case other =>
      s"${other.getName}"
  }

}
