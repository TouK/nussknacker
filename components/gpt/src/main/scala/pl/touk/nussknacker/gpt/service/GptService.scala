package pl.touk.nussknacker.gpt.service

import scala.concurrent.{ExecutionContext, Future}

trait GptService {
  def invokeCompletionService(prompt: String)(implicit ec: ExecutionContext): Future[String]
}
