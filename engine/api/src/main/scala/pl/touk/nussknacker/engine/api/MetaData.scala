package pl.touk.nussknacker.engine.api

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

// TODO: remove this trait and duplicated ProcessAdditionalFields and Group in restmodel module.
trait UserDefinedProcessAdditionalFields

case class ProcessAdditionalFields(description: Option[String],
                                   groups: Set[Group],
                                   properties: Map[String, String]) extends UserDefinedProcessAdditionalFields
case class Group(id: String, nodes: Set[String])

// todo: MetaData should hold ProcessName as id
case class MetaData(id: String,
                    typeSpecificData: TypeSpecificData,
                    isSubprocess: Boolean = false,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None,
                    subprocessVersions: Map[String, Long] = Map.empty)

sealed trait TypeSpecificData {
  def allowLazyVars : Boolean
}

case class StreamMetaData(parallelism: Option[Int] = None,
                          splitStateToDisk: Option[Boolean] = None,
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends TypeSpecificData {
  
  def checkpointIntervalDuration  : Option[Duration]= checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

  val shouldUseAsyncInterpretation : Boolean = useAsyncInterpretation.getOrElse(false)

  override val allowLazyVars: Boolean = !shouldUseAsyncInterpretation

}

object StreamMetaData {
  def empty(isSubprocess: Boolean): StreamMetaData = {
    if (isSubprocess) {
      StreamMetaData(parallelism = None)
    } else {
      StreamMetaData(parallelism = Some(1))
    }
  }
}

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData {
  override val allowLazyVars: Boolean = true
}