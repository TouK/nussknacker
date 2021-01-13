package pl.touk.nussknacker.engine.process.typeinformation

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object TypeInformationDetection extends LazyLogging {

  /*
    If custom TypeInformationDetection is registered - use it. If not, check globalParameters.useTypingResultTypeInformation parameter
    and use TypingResultAwareTypeInformationDetection for true, GenericTypeInformationDetection (default) otherwise
   */
  def forExecutionConfig(executionConfig: ExecutionConfig, classLoader: ClassLoader): TypeInformationDetection = {
    val defaultTypeInformationDetection: TypeInformationDetection = prepareDefaultTypeInformationDetection(executionConfig, classLoader)
    val detectionToUse = ScalaServiceLoader.load[TypeInformationDetection](classLoader).headOption.getOrElse(defaultTypeInformationDetection)
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

}


/**
 * This is *experimental* trait that allows for providing more details TypeInformation when ValidationContext is known
 * It *probably* will change, by default generic Flink mechanisms are used
 */
trait TypeInformationDetection extends Serializable {

  def forInterpretationResult(validationContext: ValidationContext, output: Option[TypingResult]): TypeInformation[InterpretationResult]

  def forInterpretationResults(results: Map[String, ValidationContext]): TypeInformation[InterpretationResult]

  def forContext(validationContext: ValidationContext): TypeInformation[Context]
}
