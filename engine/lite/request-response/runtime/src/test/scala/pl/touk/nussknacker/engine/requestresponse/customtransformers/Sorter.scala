package pl.touk.nussknacker.engine.requestresponse.customtransformers

import cats.Monad
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, LiteCustomComponent}

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

object Sorter extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(
      @ParamName("rank") rank: LazyParameter[java.lang.Number],
      @ParamName("maxCount") maxCount: Int,
      @ParamName("output") output: LazyParameter[AnyRef],
      @OutputVariableName outputVariable: String
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy { context =>
        val outputType = output.returnType
        context.withVariable(OutputVar.variable(outputVariable), outputType)
      }
      .implementedBy(new LiteCustomComponent with Lifecycle {

        private var runtimeContext: EngineRuntimeContext = _

        override def open(context: EngineRuntimeContext): Unit = {
          runtimeContext = context
        }

        override def createTransformation[F[_]: Monad, Result](
            continuation: DataBatch => F[ResultType[Result]],
            context: CustomComponentContext[F]
        ): DataBatch => F[ResultType[Result]] = {
          // TODO: this lazy val is tricky - we should instead assign ContextIdGenerator in open, but we don't have nodeId in open
          lazy val contextIdGenerator = runtimeContext.contextIdGenerator(context.nodeId)
          (inputCtx: DataBatch) =>
            val ranks        = inputCtx.map(rank.evaluate)
            val outputs      = inputCtx.map(output.evaluate)
            val listWithRank = ranks.zip(outputs)
            val finalList    = listWithRank.sortBy(_._1.doubleValue()).reverse.take(maxCount).map(_._2).asJava
            val sorted =
              Context(contextIdGenerator.nextContextId()).withVariable(outputVariable, finalList)
            continuation(DataBatch(sorted :: Nil))
        }
      })
  }

}
