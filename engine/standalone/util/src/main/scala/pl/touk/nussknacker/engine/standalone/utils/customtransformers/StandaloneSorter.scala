package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{CustomTransformerContext, PartInterpreterType}
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes._

import scala.collection.JavaConverters._
import scala.concurrent.Future

object StandaloneSorter extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(@ParamName("rank") rank: LazyParameter[java.lang.Number],
              @ParamName("maxCount") maxCount: Int,
              @ParamName("output") output: LazyParameter[AnyRef],
              @OutputVariableName outputVariable: String)(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy { context =>
        val outputType = output.returnType
        context.withVariable(OutputVar.variable(outputVariable), outputType)
      }
      .implementedBy(new StandaloneCustomTransformer {

        override def createTransformation(continuation: PartInterpreterType[Future],
                                          context: CustomTransformerContext): PartInterpreterType[Future] = {
          val rankInterpreter = context.syncInterpretationFunction(rank)
          val outputInterpreter = context.syncInterpretationFunction(output)
          (inputCtx: List[Context]) =>
            val ranks = inputCtx.map(rankInterpreter(_))
            val outputs = inputCtx.map(outputInterpreter(_))
            val listWithRank = ranks.zip(outputs)
            val finalList = listWithRank.sortBy(_._1.doubleValue()).reverse.take(maxCount).map(_._2).asJava
            val sorted = Context.withInitialId.withVariable(outputVariable, finalList)
            continuation(sorted :: Nil)
        }
      })
  }

  override def canHaveManyInputs: Boolean = true

}
