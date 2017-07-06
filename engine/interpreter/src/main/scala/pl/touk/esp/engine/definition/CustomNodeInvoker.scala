package pl.touk.esp.engine.definition

import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api.InterpreterMode.CustomNodeExpression
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.EspExceptionHandler
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ValidationContext}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}
import pl.touk.esp.engine.graph.node.CustomNode
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.types.EspTypeUtils
import pl.touk.esp.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

trait CustomNodeInvoker[T] {
  def run(lazyDeps: () => CustomNodeInvokerDeps): T
}

private[definition] class CustomNodeInvokerImpl[T](executor: ObjectWithMethodDef, metaData: MetaData, node: SplittedNode[CustomNode])
    extends CustomNodeInvoker[T] {

  override def run(lazyDeps: () => CustomNodeInvokerDeps) : T = {
    executor.invokeMethod(prepareParam(lazyDeps), Seq()).asInstanceOf[T]
  }

  private def prepareParam(lazyDeps: () => CustomNodeInvokerDeps)(param: String) : Option[AnyRef] = {
    val interpreter = CompilerLazyInterpreter[AnyRef](lazyDeps, metaData, node, param)
    val methodParam = EspTypeUtils.findParameterByParameterName(executor.methodDef.method, param)
    if (methodParam.exists(_.getType == classOf[LazyInterpreter[_]])) {
      Some(interpreter)
    } else {
      val emptyResult = InterpretationResult(NextPartReference(node.id), null, Context(""))
      Some(interpreter.syncInterpretationFunction(emptyResult))
    }
  }

}



case class CompilerLazyInterpreter[T](lazyDeps: () => CustomNodeInvokerDeps,
                                   metaData: MetaData,
                                   node: SplittedNode[CustomNode], param: String) extends LazyInterpreter[T] {

  override def createInterpreter(ec: ExecutionContext) = {
    createInterpreter(ec, lazyDeps())
  }

  private[definition] def createInterpreter(ec: ExecutionContext, deps: CustomNodeInvokerDeps): (InterpretationResult) => Future[T] = {
    val compiled = deps.subPartCompiler.compileWithoutContextValidation(node).getOrElse(throw new scala.IllegalArgumentException("Cannot compile"))
    (ir: InterpretationResult) => deps.interpreter.interpret(compiled.node, CustomNodeExpression(param), metaData,
      ir.finalContext)(ec).flatMap {
      case Left(result) => Future.successful(result.output.asInstanceOf[T])
      case Right(result) => Future.failed(result.throwable)
    }(ec)
  }

  //lazy val is used, interpreter creation is expensive
  @transient override lazy val syncInterpretationFunction = new SyncFunction

  class SyncFunction extends (InterpretationResult => T) with Serializable {
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

object CustomStreamTransformerExtractor extends DefinitionExtractor[CustomStreamTransformer] {

  override protected val returnType = classOf[Any]

  override protected val additionalParameters = Set[Class[_]]()

  override protected def extractParameterType(p: java.lang.reflect.Parameter) =
    EspTypeUtils.extractParameterType(p, classOf[LazyInterpreter[_]])


}



