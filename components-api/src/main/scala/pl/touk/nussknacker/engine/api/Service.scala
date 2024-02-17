package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.component.{AllProcessingModesComponent, Component}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

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
abstract class Service extends Lifecycle with Component with AllProcessingModesComponent

/*
  This is marker interface, for services which have Lazy/dynamic parameters. Invocation is handled with ServiceRuntimeLogic
  Lifecycle is handled on EagerService level (like in standard Service).
  A sample use case is as follows:
    - Enrichment with data from SQL database, ConnectionPool is created on level of EagerService
    - Each ServiceRuntimeLogic has different SQL query, ServiceRuntimeLogic stores PreparedStatement
  Please see EagerLifecycleService to see how such scenario can be achieved.
 */
// TODO: EagerService shouldn't extend Lifecycle, instead ServiceRuntimeLogic should extend it - see notice in ProcessCompilerData.lifecycle
abstract class EagerService extends Service

trait ServiceRuntimeLogic {

  def apply(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[Any]

}
