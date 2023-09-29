package pl.touk.nussknacker.gpt

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.{Parameter, SpelTemplateParameterEditor}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.util.service.{EagerServiceWithStaticParametersAndReturnType, TimeMeasuringService}
import pl.touk.nussknacker.gpt.ChatGptComponent._
import pl.touk.nussknacker.gpt.service.GptService

import scala.concurrent.{ExecutionContext, Future}

// TODO: for now parameters will be static, but in the future, let's user configure how they will be translated to spel template
class ChatGptComponent(serviceProvider: MetaData => GptService)
  extends EagerServiceWithStaticParametersAndReturnType with TimeMeasuringService with LazyLogging {

  override val parameters: List[Parameter] = List(promptParameter)

  override val returnType: typing.TypingResult = Typed.fromDetailedType[String]

  override def invoke(params: Map[String, Any])
                     (implicit ec: ExecutionContext,
                      collector: InvocationCollectors.ServiceInvocationCollector,
                      contextId: ContextId,
                      metaData: MetaData,
                      componentUseCase: ComponentUseCase): Future[Any] = {
    measuring {
      val prompt = params(promptParameter.name).asInstanceOf[String]
      serviceProvider(metaData).invokeCompletionService(prompt)
    }
  }

  override protected def serviceName: String = ChatGptComponent.serviceName

}

object ChatGptComponent {

  // It is a little bit tricky - for SpelTemplateParameterEditor, Parameter has to be optional because default it doesn't need to be wrapped in ''
  val promptParameter: Parameter = Parameter.optional[String]("prompt").copy(
    isLazyParameter = true,
    editor = Some(SpelTemplateParameterEditor)
  )

  val serviceName = "chatgpt"

}
