package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{CustomTransformerContext, PartInterpreterType}
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes.StandaloneCustomTransformer

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("parts") parts: LazyParameter[java.util.Collection[Any]],
             @OutputVariableName outputVariable: String): StandaloneCustomTransformer = {
    new ProcessSplitter(parts, outputVariable)
  }

}

class ProcessSplitter(parts: LazyParameter[java.util.Collection[Any]], outputVariable: String)
  extends StandaloneCustomTransformer with ReturningType {

  override def createTransformation(continuation: PartInterpreterType[Future],
                                    context: CustomTransformerContext): PartInterpreterType[Future] = {
    val interpreter = context.syncInterpretationFunction(parts)
    (ctxs: List[Context]) => {
      val partsToInterpret = ctxs.flatMap { ctx =>
        val partsToRun = interpreter(ctx)
        partsToRun.asScala.toList.map { partToRun =>
          ctx.withVariable(outputVariable, partToRun)
        }
      }
      continuation(partsToInterpret)
    }
  }

  override def returnType: typing.TypingResult = {
    parts.returnType match {
      case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) && tc.objType.params.nonEmpty =>
        tc.objType.params.head
      case _ => Unknown
    }
  }
}