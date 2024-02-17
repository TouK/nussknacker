package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.compile.nodecompilation.ParameterEvaluator
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.ComponentWithRuntimeLogicFactory

// This class helps to create an Executor using Component. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
class ComponentRuntimeLogicCreator(parameterEvaluator: ParameterEvaluator) extends LazyLogging {

  def createRuntimeLogic[ComponentExecutor](
                                             componentDefWithImpl: ComponentWithRuntimeLogicFactory,
                                             compiledParameters: List[(TypedParameter, Parameter)],
                                             outputVariableNameOpt: Option[String],
                                             additionalDependencies: Seq[AnyRef],
                                             componentUseCase: ComponentUseCase
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ComponentExecutor] = {
    NodeValidationExceptionHandler.handleExceptions {
      doCreateRuntimeLogic[ComponentExecutor](
        componentDefWithImpl,
        compiledParameters,
        outputVariableNameOpt,
        additionalDependencies,
        componentUseCase
      )
    }
  }

  private def doCreateRuntimeLogic[ComponentExecutor](
                                                       componentDefWithImpl: ComponentWithRuntimeLogicFactory,
                                                       params: List[(TypedParameter, Parameter)],
                                                       outputVariableNameOpt: Option[String],
                                                       additional: Seq[AnyRef],
                                                       componentUseCase: ComponentUseCase
  )(implicit processMetaData: MetaData, nodeId: NodeId): ComponentExecutor = {
    val paramsMap = params.map { case (tp, p) =>
      p.name -> parameterEvaluator.prepareParameter(tp, p)._1
    }.toMap
    componentDefWithImpl.runtimeLogicFactory
      .createRuntimeLogic(paramsMap, outputVariableNameOpt, Seq(processMetaData, nodeId, componentUseCase) ++ additional)
      .asInstanceOf[ComponentExecutor]
  }

}
