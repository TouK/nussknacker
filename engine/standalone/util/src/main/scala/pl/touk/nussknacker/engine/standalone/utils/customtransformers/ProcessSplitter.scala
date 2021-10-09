package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes._

import scala.collection.JavaConverters._

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("parts") parts: LazyParameter[java.util.Collection[Any]]): StandaloneCustomTransformer = {
    new ProcessSplitter(parts)
  }

}

class ProcessSplitter(parts: LazyParameter[java.util.Collection[Any]])
  extends StandaloneCustomTransformer with ReturningType {

  override def createTransformation(outputVariable: Option[String]): StandaloneCustomTransformation =
    (continuation: InterpreterType, lpi: LazyParameterInterpreter) => {
      val interpreter = lpi.syncInterpretationFunction(parts)
      (ctxs: List[Context]) => {
        val partsToInterpret = ctxs.flatMap { ctx =>
          val partsToRun = interpreter(ctx)
          partsToRun.asScala.toList.map { partToRun =>
            ctx.withVariable(outputVariable.get, partToRun)
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