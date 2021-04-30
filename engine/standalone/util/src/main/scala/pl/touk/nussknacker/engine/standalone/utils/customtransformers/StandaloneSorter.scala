package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.standalone.api.StandaloneCustomTransformer

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

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
        override def createTransformation(ov: Option[String]): StandaloneCustomTransformation = {
          (outputContinuation, lpi) => {
            val rankInterpreter = lpi.createInterpreter(rank)
            val outputInterpreter = lpi.createInterpreter(output)
            (inputCtx: List[Context], ec) =>
              implicit val iec: ExecutionContext = ec
              val zipped = for {
                ranks <- Future.sequence(inputCtx.map(rankInterpreter(ec, _)))
                outputs <- Future.sequence(inputCtx.map(outputInterpreter(ec, _)))
              } yield ranks.zip(outputs)
              zipped.map { listWithRank =>
                val finalList = listWithRank.sortBy(_._1.doubleValue()).reverse.take(maxCount).map(_._2).asJava
                Context("").withVariable(outputVariable, finalList)
              }.flatMap(c => outputContinuation(c :: Nil, ec))
          }
        }
      })
  }

  override def canHaveManyInputs: Boolean = true

}
