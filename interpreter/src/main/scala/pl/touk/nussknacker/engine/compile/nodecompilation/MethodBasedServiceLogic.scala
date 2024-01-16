package pl.touk.nussknacker.engine.compile.nodecompilation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.OutputVar
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithLogic

import scala.concurrent.{ExecutionContext, Future}

private[nodecompilation] class MethodBasedServiceLogic(
    metaData: MetaData,
    nodeId: NodeId,
    outputVariableNameOpt: Option[OutputVar],
    componentDefWithImpl: ComponentDefinitionWithLogic
) extends ServiceLogic
    with LazyLogging {

  override def run(
      paramsEvaluator: ServiceLogic.ParamsEvaluator
  )(implicit runContext: ServiceLogic.RunContext, executionContext: ExecutionContext): Future[Any] = {
    componentDefWithImpl.componentLogic
      .run(
        paramsEvaluator.evaluate().allRaw,
        outputVariableNameOpt = outputVariableNameOpt.map(_.outputName),
        additional = Seq(
          executionContext,
          runContext.collector,
          metaData,
          nodeId,
          runContext.contextId,
          runContext.componentUseCase
        )
      )
      .asInstanceOf[Future[AnyRef]]
  }

}
