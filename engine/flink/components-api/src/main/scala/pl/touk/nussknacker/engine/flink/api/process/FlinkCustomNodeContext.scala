package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{Context, JobData, MetaData, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

case class FlinkCustomNodeContext(
    jobData: JobData,
    // TODO: it can be used in state recovery - make sure that it won't change during renaming of nodes on gui
    nodeId: String,
    timeout: FiniteDuration,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    lazyParameterHelper: FlinkLazyParameterFunctionHelper,
    exceptionHandlerPreparer: RuntimeContext => ExceptionHandler,
    globalParameters: Option[NkGlobalParameters],
    validationContext: Either[ValidationContext, Map[String, ValidationContext]],
    componentUseContext: ComponentUseContext,
) {
  def metaData: MetaData = jobData.metaData

  lazy val contextTypeInfo: TypeInformation[Context] =
    TypeInformationDetection.instance.forContext(asOneOutputContext)

  lazy val valueWithContextInfo = new ValueWithContextInfo

  class ValueWithContextInfo {

    lazy val forUnknown: TypeInformation[ValueWithContext[AnyRef]] = forType[AnyRef](Unknown)

    def forNull[T]: TypeInformation[ValueWithContext[T]] =
      forType(TypeInformationDetection.instance.forNull[T])

    def forBranch[T](key: String, value: TypingResult): TypeInformation[ValueWithContext[T]] =
      forBranch(key, TypeInformationDetection.instance.forType[T](value))

    def forBranch[T](key: String, value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
      TypeInformationDetection.instance.forValueWithContext(asJoinContext(key), value)

    def forType[T](value: TypingResult): TypeInformation[ValueWithContext[T]] =
      forType[T](TypeInformationDetection.instance.forType[T](value))

    def forType[T](value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
      TypeInformationDetection.instance.forValueWithContext(asOneOutputContext, value)

    def forClass[T](klass: Class[_]): TypeInformation[ValueWithContext[T]] =
      forType[T](Typed.typedClass(klass))

    def forClass[T: ClassTag]: TypeInformation[ValueWithContext[T]] =
      forType(TypeInformationDetection.instance.forClass[T])

  }

  def branchValidationContext(branchId: String): ValidationContext = asJoinContext.getOrElse(
    branchId,
    throw new IllegalArgumentException(s"No validation context for branchId [$branchId] is defined")
  )

  private def asOneOutputContext: ValidationContext =
    validationContext.left.getOrElse(
      throw new IllegalArgumentException(
        "This node is a join, asJoinContext should be used to extract validation context"
      )
    )

  private def asJoinContext: Map[String, ValidationContext] =
    validationContext.getOrElse(
      throw new IllegalArgumentException(
        "This node is a single input node. asOneOutputContext should be used to extract validation context"
      )
    )

}
