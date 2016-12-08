package pl.touk.esp.engine.definition

import java.lang.reflect.ParameterizedType

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
    val values = executor.orderedParameters.prepareValues(prepareParam(lazyDeps), Seq(() => lazyDeps().exceptionHandler))
    executor.invokeMethod(values).asInstanceOf[T]
  }

  private def prepareParam(lazyDeps: () => CustomNodeInvokerDeps)(param: Parameter) = {
    val interpreter = CompilerLazyInterpreter[Any](lazyDeps, metaData, node, param)
    val methodParam = EspTypeUtils.findParameterByParameterName(executor.methodDef.method, param.name)
    if (methodParam.exists(_.getType ==  classOf[LazyInterpreter[_]])) {
      interpreter
    } else {
      val emptyResult = InterpretationResult(NextPartReference(node.id), null, Context())
      interpreter.syncInterpretationFunction(emptyResult)
    }
  }

}



case class CompilerLazyInterpreter[T](lazyDeps: () => CustomNodeInvokerDeps,
                                   metaData: MetaData,
                                   node: SplittedNode[CustomNode], param: Parameter) extends LazyInterpreter[T] {

  override def createInterpreter(ec: ExecutionContext) = {
    createInterpreter(ec, lazyDeps())
  }

  private[definition] def createInterpreter(ec: ExecutionContext, deps: CustomNodeInvokerDeps): (InterpretationResult) => Future[T] = {
    val compiled = deps.subPartCompiler.compileWithoutContextValidation(node).getOrElse(throw new scala.IllegalArgumentException("Cannot compile"))
    (ir: InterpretationResult) => deps.interpreter.interpret(compiled.node, CustomNodeExpression(param.name), metaData,
      ir.finalContext)(ec).map(_.output.asInstanceOf[T])(ec)
  }

  //lazy val jest po to, zeby za kazdym razem nie tworzyc interpretera - bo to kosztowne b.
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

  override protected val additionalParameters = Set[Class[_]](classOf[() => EspExceptionHandler])

  override protected def extractParameterType(p: java.lang.reflect.Parameter) =
    EspTypeUtils.extractParameterType(p, classOf[LazyInterpreter[_]])


}



