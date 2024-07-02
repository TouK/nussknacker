package pl.touk.nussknacker.engine.kafka.validator

import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName

trait TopicsExistenceValidator extends Serializable {

  final def validateTopic[T <: TopicName](topic: T): Validated[TopicExistenceValidationException[T], T] =
    validateTopics(NonEmptyList.one(topic)).map(_.head)

  def validateTopics[T <: TopicName](
      topics: NonEmptyList[T]
  ): Validated[TopicExistenceValidationException[T], NonEmptyList[T]]

}

final case class TopicExistenceValidationException[T <: TopicName](topics: NonEmptyList[T])
    extends RuntimeException(TopicExistenceValidationException.message(topics)) {

  def toCustomNodeError(nodeId: String, paramName: Option[ParameterName]) =
    new CustomNodeError(nodeId, super.getMessage, paramName)
}

object TopicExistenceValidationException {

  private def message[T <: TopicName](topics: NonEmptyList[T]): String =
    topics.tail match {
      case Nil => s"Topic ${topics.head.toUnspecialized.name} doesn't exist"
      case _   => s"Topics ${topics.toList.map(_.toUnspecialized.name).mkString(", ")} do not exist"
    }

}
