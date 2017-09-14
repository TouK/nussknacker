package pl.touk.nussknacker.engine.api

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

trait UserDefinedProcessAdditionalFields

case class MetaData(id: String, 
                    typeSpecificData: TypeSpecificData,
                    isSubprocess: Boolean = false,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None,
                    subprocessVersions: Map[String, Long] = Map.empty
                   )

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

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData {
  override val allowLazyVars: Boolean = true
}