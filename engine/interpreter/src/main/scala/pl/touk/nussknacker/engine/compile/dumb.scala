package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.{CustomNodeInvoker, CustomNodeInvokerDeps, ProcessObjectFactory, ServiceInvoker}

import scala.concurrent.ExecutionContext

object dumb {

  object DumbServiceInvoker extends ServiceInvoker {

    override def invoke(params: Map[String, Any], nodeContext: NodeContext)
                       (implicit ec: ExecutionContext, metaData: MetaData) = throw new IllegalAccessException("Dumb service shouldn't be invoked")

  }

  class DumbProcessObjectFactory[T] extends ProcessObjectFactory[T] {
    override  def create(params: List[evaluatedparam.Parameter])(implicit processMetaData: MetaData, nodeId: NodeId): T =
      null.asInstanceOf[T]
  }

  class DumbCustomNodeInvoker[T] extends CustomNodeInvoker[T] {
    override def run(lazyDeps: () => CustomNodeInvokerDeps) = null.asInstanceOf[T]
  }

  object DumbCustomStreamTransformer extends CustomStreamTransformer

}
