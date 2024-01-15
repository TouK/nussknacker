package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.ServiceLogic.{ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors

import scala.concurrent.{ExecutionContext, Future}

/**
  * Interface of Enricher/Processor. It has to have one method annotated with
  * [[pl.touk.nussknacker.engine.api.MethodToInvoke]]. This method is called for every service invocation.
  *
  * This could be scala-trait, but we leave it as abstract class for now for java compatibility.
  *
  * TODO We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
  * from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.) unless open() *or* appropriate @MethodToInvoke
  *  is called
  */
abstract class Service extends Lifecycle with Component

/*
  This is marker interface, for services which have Lazy/dynamic parameters. Invocation is handled with ServiceInvoker
  Lifecycle is handled on EagerService level (like in standard Service).
  A sample use case is as follows:
    - Enrichment with data from SQL database, ConnectionPool is created on level of EagerService
    - Each ServiceInvoker has different SQL query, ServiceInvoker stores PreparedStatement
  Please see EagerLifecycleService to see how such scenario can be achieved.
 */
// TODO: EagerService shouldn't extend Lifecycle, instead ServiceInvoker should extend it - see notice in ProcessCompilerData.lifecycle
abstract class EagerService extends Service

trait ServiceLogic {

  def run(
      paramsEvaluator: ParamsEvaluator
  )(implicit runContext: RunContext, executionContext: ExecutionContext): Future[Any]

}

object ServiceLogic {

  final case class RunContext(
      collector: InvocationCollectors.ServiceInvocationCollector,
      contextId: ContextId,
      componentUseCase: ComponentUseCase
  )

  trait ParamsEvaluator {
    def evaluate(additionalVariables: Map[String, Any] = Map.empty): EvaluatedParams
  }

  object ParamsEvaluator {
    def create(context: Context, evaluate: Context => Map[String, Any]): ParamsEvaluator =
      new FunctionBasedParamsEvaluator(context, evaluate)
  }

  private[ServiceLogic] final class FunctionBasedParamsEvaluator private[ServiceLogic] (
      context: Context,
      evaluate: Context => Map[String, Any]
  ) extends ParamsEvaluator {

    override def evaluate(additionalVariables: Map[String, Any] = Map.empty): EvaluatedParams = {
      val newContext = context.withVariables(additionalVariables)
      new EvaluatedParams(evaluate(newContext))
    }

  }

  final class EvaluatedParams(val allRaw: Map[String, Any]) {
    def get[T](name: String): Option[T] = allRaw.get(name).map(_.asInstanceOf[T])

    def getUnsafe[T](name: String): T = get(name).getOrElse {
      throw new IllegalArgumentException(
        s"Cannot find param with name [$name]. Present ones: [${allRaw.keys.mkString(", ")}]"
      )
    }

  }

}
