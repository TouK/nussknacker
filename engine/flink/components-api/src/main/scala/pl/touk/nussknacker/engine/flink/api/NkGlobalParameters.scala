package pl.touk.nussknacker.engine.flink.api

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters
import pl.touk.nussknacker.engine.api.ProcessVersion

import java.util
import scala.collection.JavaConverters._

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flink REST API/webconsole
case class NkGlobalParameters(buildInfo: String,
                              processVersion: ProcessVersion,
                              configParameters: Option[ConfigGlobalParameters],
                              namingParameters: Option[NamingParameters],
                              additionalInformation: Map[String, String]) extends GlobalJobParameters {

  //here we decide which configuration properties should be shown in REST API etc.
  //NOTE: some of the information is used in FlinkRestManager - any changes here should be reflected there
  override def toMap: util.Map[String, String] = {

    val baseProperties = Map[String, String](
      "buildInfo" -> buildInfo,
      "versionId" -> processVersion.versionId.value.toString,
      "processId" -> processVersion.processId.value.toString,
      "modelVersion" -> processVersion.modelVersion.map(_.toString).orNull,
      "user" -> processVersion.user
    )
    val configMap = baseProperties ++ additionalInformation
    //we wrap in HashMap because .asJava creates not-serializable map in 2.11
    new util.HashMap(configMap.filterNot(_._2 == null).asJava)
  }

}

//this is part of global parameters that is parsed with typesafe Config (e.g. from application.conf/model.conf)
case class ConfigGlobalParameters(explicitUidInStatefulOperators: Option[Boolean],
                                  useTypingResultTypeInformation: Option[Boolean],
                                  //TODO: temporary, until we confirm that IOMonad is not causing problems
                                  useIOMonadInInterpreter: Option[Boolean])

case class NamingParameters(tags: Map[String, String])

object NkGlobalParameters {

  def create(buildInfo: String,
            processVersion: ProcessVersion,
            modelConfig: Config,
            namingParameters: Option[NamingParameters],
            additionalInformation: Map[String, String]): NkGlobalParameters = {
    val configGlobalParameters = modelConfig.getAs[ConfigGlobalParameters]("globalParameters")
    NkGlobalParameters(buildInfo, processVersion, configGlobalParameters, namingParameters, additionalInformation)
  }

  def setInContext(ec: ExecutionConfig, globalParameters: NkGlobalParameters): Unit = {
    ec.setGlobalJobParameters(globalParameters)
  }

  def readFromContext(ec: ExecutionConfig): Option[NkGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a: NkGlobalParameters => a
  }

}
