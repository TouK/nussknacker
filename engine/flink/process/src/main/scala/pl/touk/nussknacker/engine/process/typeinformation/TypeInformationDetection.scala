package pl.touk.nussknacker.engine.process.typeinformation

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object TypeInformationDetectionUtils extends LazyLogging {

  /*
    If custom TypeInformationDetection is registered - use it. If not, check globalParameters.useTypingResultTypeInformation parameter
    and use TypingResultAwareTypeInformationDetection for true, GenericTypeInformationDetection (default) otherwise
   */
  def forExecutionConfig(executionConfig: ExecutionConfig, classLoader: ClassLoader): TypeInformationDetection = {
    val detectionToUse = ScalaServiceLoader.loadClass[TypeInformationDetection](classLoader) {
      prepareDefaultTypeInformationDetection(executionConfig, classLoader)
    }
    logger.info(s"Using TypeInformationDetection: $detectionToUse")
    detectionToUse
  }

  private def prepareDefaultTypeInformationDetection(executionConfig: ExecutionConfig, classLoader: ClassLoader) = {
    val useTypingResultTypeInformation = NkGlobalParameters.readFromContext(executionConfig)
      .flatMap(_.configParameters).flatMap(_.useTypingResultTypeInformation).getOrElse(false)
    if (useTypingResultTypeInformation) {
      TypingResultAwareTypeInformationDetection(classLoader)
    } else {
      GenericTypeInformationDetection
    }
  }

}

object GenericTypeInformationDetection extends TypeInformationDetection {

  import org.apache.flink.api.scala._

  override def forInterpretationResult(validationContext: ValidationContext, output: Option[TypingResult]): TypeInformation[InterpretationResult] = implicitly[TypeInformation[InterpretationResult]]

  override def forInterpretationResults(results: Map[String, ValidationContext]): TypeInformation[InterpretationResult] = implicitly[TypeInformation[InterpretationResult]]

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] = implicitly[TypeInformation[Context]]

  override def forValueWithContext[T](validationContext: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]]
    = implicitly[TypeInformation[ValueWithContext[AnyRef]]].asInstanceOf[TypeInformation[ValueWithContext[T]]]

  override def forType(typingResult: TypingResult): TypeInformation[Any] = implicitly[TypeInformation[Any]]
}
