package pl.touk.nussknacker.engine.api.process

sealed trait TopicName

object TopicName {
  final case class ForSource(name: String) extends TopicName
  final case class ForSink(name: String)   extends TopicName
}
