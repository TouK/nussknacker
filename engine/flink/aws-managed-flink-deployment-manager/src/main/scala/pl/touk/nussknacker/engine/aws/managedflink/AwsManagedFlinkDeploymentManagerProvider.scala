package pl.touk.nussknacker.engine.aws.managedflink

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.assembly.{MergeRule, MergeStrategy}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterConfig
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.ScenarioTestingConfig
import software.amazon.awssdk.regions.Region

import java.io.File
import java.net.URL
import scala.concurrent.duration.FiniteDuration

class AwsManagedFlinkDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    import AwsManagedFlinkDeploymentManagerConfig._
    val flinkConfig = deploymentConfig.rootAs[AwsManagedFlinkDeploymentManagerConfig]
    import dependencies._
    Validated.validNel(
      new AwsManagedFlinkDeploymentManager(
        modelDataProvider = modelDataProvider,
        config = flinkConfig,
        additionalModelUrls = implicitlyIncludedAwsDependencies
      )
    )
  }

  override def name: String = "awsManagedFlink"

  override def defaultEngineSetupName: EngineSetupName = EngineSetupName("AWS Managed Flink")

  // TODO: add scenario properties
  override def metaDataInitializer(config: Config): MetaDataInitializer = MetaDataInitializer.apply("CustomMetaData")

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] = Map.empty

}

final case class AwsManagedFlinkDeploymentManagerConfig(
    awsAccessKeyId: String,
    awsSecretAccessKey: String,
    serviceExecutionRoleArn: String,
    region: Region,
    bucketName: String,
    // TODO: logStreamArn is least limiting. It could also be configured through accountId, logGroupName, logStreamName
    cloudWatchLogStreamArn: Option[String],
    applicationJarMergeRules: List[MergeRule] = List.empty,
    miniCluster: FlinkMiniClusterConfig = FlinkMiniClusterConfig(),
    scenarioTesting: ScenarioTestingConfig = ScenarioTestingConfig(),
)

object AwsManagedFlinkDeploymentManagerConfig {

  import net.ceedubs.ficus.Ficus.stringValueReader
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  implicit val mergeStrategyValueReader: ValueReader[MergeStrategy] =
    ValueReader[String].map { raw =>
      raw.trim.toLowerCase match {
        case "concat"              => MergeStrategy.Concat
        case "discard"             => MergeStrategy.Discard
        case "deduplicate"         => MergeStrategy.Deduplicate
        case "filterdistinctlines" => MergeStrategy.FilterDistinctLines
        case "first"               => MergeStrategy.First
        case "rename"              => MergeStrategy.Rename
        case other                 => throw new IllegalArgumentException(s"Unknown merge strategy: $other")
      }
    }

  implicit val regionValueReader: ValueReader[Region] =
    ValueReader[String].map { str =>
      Region.of(str)
    }

  implicit val flinkMiniClusterReader: ValueReader[FlinkMiniClusterConfig] =
    ValueReader[FlinkMiniClusterConfig]

  lazy val implicitlyIncludedAwsDependencies: List[URL] = {
    val additionalJarPaths = List(
      "flink-dropwizard-metrics-deps/flink-metrics-dropwizard.jar",
      "flink-dropwizard-metrics-deps/dropwizard-metrics-core.jar",
      "model/awsManagedFlinkExecutorPlugin.jar"
    )
    val additionalJarUrls = additionalJarPaths
      .map(filename => new File(filename))
      .filter(_.exists())
      .map(_.toURI.toURL)
    additionalJarUrls
  }

}
