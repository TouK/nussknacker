package pl.touk.nussknacker.engine.process.typeinformation

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation, Types}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.process.typeinformation.internal.{ContextTypeHelpers, ValueWithContextTypeHelpers}
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
    val useTypingResultTypeInformation = NkGlobalParameters
      .readFromContext(executionConfig)
      .flatMap(_.configParameters)
      .flatMap(_.useTypingResultTypeInformation)
      .getOrElse(true)
    if (useTypingResultTypeInformation) {
      TypingResultAwareTypeInformationDetection(classLoader)
    } else {
      GenericTypeInformationDetection
    }
  }

}

object GenericTypeInformationDetection extends TypeInformationDetection {

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] =
    ContextTypeHelpers.infoFromVariablesAndParentOption(
      TypeInformation.of(new TypeHint[Map[String, Any]] {}),
      validationContext.parent.map(forContext)
    )

  override def forValueWithContext[T](
      validationContext: ValidationContext,
      value: TypeInformation[T]
  ): TypeInformation[ValueWithContext[T]] =
    ValueWithContextTypeHelpers.infoFromValueAndContext(value, forContext(validationContext))

  override def forType[T](typingResult: TypingResult): TypeInformation[T] = {
    val classNeeded = typingResult match {
      case e: SingleTypingResult => e.objType.klass
      case _                     => classOf[AnyRef]
    }
    TypeInformation.of(classNeeded).asInstanceOf[TypeInformation[T]]
  }

}
