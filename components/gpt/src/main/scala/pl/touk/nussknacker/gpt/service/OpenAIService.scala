package pl.touk.nussknacker.gpt.service
import com.typesafe.scalalogging.LazyLogging
import io.circe.Codec
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import org.asynchttpclient.AsyncHttpClient
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.gpt.GptComponentsConfig
import pl.touk.nussknacker.gpt.service.ChatRole.ChatRole
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.circe.asJson
import sttp.client3.{UriContext, basicRequest}
import sttp.model.{MediaType, Method, Uri}

import scala.concurrent.{ExecutionContext, Future}
class OpenAIService(client: AsyncHttpClient, gptConfig: GptComponentsConfig) extends GptService with LazyLogging {

  protected val chatCompletionsApiUri: Uri = uri"https://api.openai.com/v1/chat/completions"

  override def invokeCompletionService(prompt: String)(implicit ec: ExecutionContext): Future[String] = {
    val backend = AsyncHttpClientFutureBackend.usingClient(client)
    val requestBody = ChatRequest(gptConfig.model, List(ChatMessage(ChatRole.User, prompt))).asJson.noSpaces
    logger.debug(s"Request: $requestBody")
    val request = basicRequest
      .method(Method.POST, chatCompletionsApiUri)
      .contentType(MediaType.ApplicationJson)
      .auth.bearer(gptConfig.openaAIApiKey)
      .body(requestBody)
      .response(asJson[ChatResponse])
    request
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map { response =>
        logger.debug(s"Response: $response")
        val choice = response.choices.headOption.getOrElse(throw new IllegalStateException("Returned empty choices"))
        assume(choice.message.role == ChatRole.Assistant, s"Returned role in message other than ${ChatRole.Assistant}: ${choice.message.role}")
        choice.message.content.trim
      }
  }
}

@JsonCodec case class ChatRequest(model: String, messages: List[ChatMessage])

@JsonCodec case class ChatMessage(role: ChatRole, content: String)

object ChatRole extends Enumeration {
  type ChatRole = Value

  val System: Value = Value("system")
  val User: Value = Value("user")
  val Assistant: Value = Value("assistant")

  implicit val codec: Codec[ChatRole] = Codec.codecForEnumeration(ChatRole)
}

@JsonCodec case class ChatResponse(choices: List[ChatChoice])

@JsonCodec case class ChatChoice(message: ChatMessage)