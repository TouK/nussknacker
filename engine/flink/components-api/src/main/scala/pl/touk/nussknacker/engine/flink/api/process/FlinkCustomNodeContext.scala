package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.component.NodeDeploymentData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Context, JobData, MetaData, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.concurrent.duration.FiniteDuration

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
    typeInformationDetection: TypeInformationDetection,
    componentUseCase: ComponentUseCase,
    nodeDeploymentData: Option[NodeDeploymentData]
) {
  def metaData: MetaData = jobData.metaData

  lazy val contextTypeInfo: TypeInformation[Context] =
    typeInformationDetection.forContext(asOneOutputContext)

  val valueWithContextInfo = new valueWithContextInfo

  class valueWithContextInfo {

    def forCustomContext[T](ctx: ValidationContext, value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
      typeInformationDetection.forValueWithContext(ctx, value)

    def forCustomContext[T](ctx: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]] =
      typeInformationDetection.forValueWithContext(ctx, value)

    def forBranch[T](key: String, value: TypingResult): TypeInformation[ValueWithContext[T]] =
      forCustomContext(asJoinContext(key), value)

    def forBranch[T](key: String, value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
      forCustomContext(asJoinContext(key), value)

    def forType[T](value: TypingResult): TypeInformation[ValueWithContext[T]] =
      forCustomContext(asOneOutputContext, value)

    def forType[T](value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
      forCustomContext(asOneOutputContext, value)

    lazy val forUnknown: TypeInformation[ValueWithContext[AnyRef]] = forType[AnyRef](Unknown)
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
