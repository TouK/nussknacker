package pl.touk.nussknacker.gpt.service
import scala.concurrent.{ExecutionContext, Future}

object EchoGtpService extends GptService {
  override def invokeCompletionService(prompt: String)(implicit ec: ExecutionContext): Future[String] =
    Future.successful(prompt)

}
