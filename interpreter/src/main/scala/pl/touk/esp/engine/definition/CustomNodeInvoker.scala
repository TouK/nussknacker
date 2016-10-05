package pl.touk.esp.engine.definition

import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api.InterpreterMode.CustomNodeExpression
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.EspExceptionHandler
import pl.touk.esp.engine.compile.PartSubGraphCompiler
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}
import pl.touk.esp.engine.graph.node.CustomNode
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

trait CustomNodeInvoker[T] {
  def run(lazyDeps: () => CustomNodeInvokerDeps): T
}

private[definition] class CustomNodeInvokerImpl[T](executor: ObjectWithMethodDef, metaData: MetaData, node: SplittedNode[CustomNode])
    extends CustomNodeInvoker[T] {

  override def run(lazyDeps: () => CustomNodeInvokerDeps) : T = {
    def prepareParams(param: Parameter) = CompilerLazyInterpreter(lazyDeps, metaData, node, param)
    val values = executor.orderedParameters.prepareValues(prepareParams, Seq(() => lazyDeps().exceptionHandler))
    executor.method.invoke(executor.obj, values: _*).asInstanceOf[T]
  }

}



case class CompilerLazyInterpreter(lazyDeps: () => CustomNodeInvokerDeps,
                                   metaData: MetaData,
                                   node: SplittedNode[CustomNode], param: Parameter) extends LazyInterpreter {

  override def createInterpreter(ec: ExecutionContext) = {
    createInterpreter(ec, lazyDeps())
  }

  private[definition] def createInterpreter(ec: ExecutionContext, deps: CustomNodeInvokerDeps): (InterpretationResult) => Future[InterpretationResult] = {
    val compiled = deps.subPartCompiler.compile(node).getOrElse(throw new scala.IllegalArgumentException("Cannot compile"))
    (ir: InterpretationResult) => deps.interpreter.interpret(compiled, CustomNodeExpression(param.name), metaData,
      ir.finalContext)(ec)
  }

  //lazy val jest po to, zeby za kazdym razem nie tworzyc interpretera - bo to kosztowne b.
  @transient override lazy val syncInterpretationFunction = new SyncFunction

  class SyncFunction extends (InterpretationResult => InterpretationResult) with Serializable {
    lazy implicit val ec = SynchronousExecutionContext.ctx
    lazy val deps = lazyDeps()
    lazy val interpreter = createInterpreter(ec, deps)

    override def apply(v1: InterpretationResult) = {
      Await.result(interpreter(v1), deps.processTimeout)
    }

  }
}

object CustomNodeInvoker {

  def apply[T](executor: ObjectWithMethodDef, metaData: MetaData, node: SplittedNode[CustomNode]) =
    new CustomNodeInvokerImpl[T](executor, metaData, node)

}


trait CustomNodeInvokerDeps {
  def interpreter: Interpreter
  def subPartCompiler: PartSubGraphCompiler
  def processTimeout: FiniteDuration
  def exceptionHandler: EspExceptionHandler
}

object CustomStreamTransforerExtractor extends DefinitionExtractor[CustomStreamTransformer] {

  override protected val returnType = classOf[Any]

  override protected val additionalParameters = Set[Class[_]](classOf[() => EspExceptionHandler])

}


