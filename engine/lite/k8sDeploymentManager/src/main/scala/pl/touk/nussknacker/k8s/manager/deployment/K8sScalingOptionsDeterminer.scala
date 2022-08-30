package pl.touk.nussknacker.k8s.manager.deployment

import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig._

trait K8sScalingOptionsDeterminer {

  def determine(parallelism: Int): K8sScalingOptions

}

case class K8sScalingOptions(replicasCount: Int, noOfTasksInReplica: Int)

object K8sScalingOptionsDeterminer {

  def create(config: Option[K8sScalingConfig]): Option[K8sScalingOptionsDeterminer] = {
    config match {
      case None | Some(NotDefinedConfig) => None
      case Some(fixedReplicas: FixedReplicasCountConfig) => Some(new FixedReplicasCountK8sScalingOptionsDeterminer(fixedReplicas.fixedReplicasCount))
      case Some(dividingParallelism: DividingParallelismConfig) => Some(new DividingParallelismK8sScalingOptionsDeterminer(dividingParallelism))
    }
  }

}

class FixedReplicasCountK8sScalingOptionsDeterminer(val replicasCount: Int) extends K8sScalingOptionsDeterminer {

  override def determine(parallelism: Int): K8sScalingOptions = {
    val noOfTasksInReplica = Math.ceil(parallelism.toDouble / replicasCount).toInt
    K8sScalingOptions(replicasCount, noOfTasksInReplica)
  }

}

class DividingParallelismK8sScalingOptionsDeterminer(config: DividingParallelismConfig) extends K8sScalingOptionsDeterminer {

  override def determine(parallelism: Int): K8sScalingOptions = {
    val replicasCount = Math.ceil(parallelism.toDouble / config.tasksPerReplica).toInt
    val noOfTasksInReplica = Math.ceil(parallelism.toDouble / replicasCount).toInt
    K8sScalingOptions(replicasCount, noOfTasksInReplica)
  }

}

sealed trait K8sScalingConfig

object K8sScalingConfig {

  case object NotDefinedConfig extends K8sScalingConfig

  case class FixedReplicasCountConfig(fixedReplicasCount: Int) extends K8sScalingConfig

  case class DividingParallelismConfig(tasksPerReplica: Int) extends K8sScalingConfig

  val fixedReplicasCountPath = "fixedReplicasCount"

  val tasksPerReplicaPath = "tasksPerReplica"

  implicit def valueReader: ValueReader[K8sScalingConfig] = Ficus.configValueReader.map { config =>
    (config.hasPath(fixedReplicasCountPath), config.hasPath(tasksPerReplicaPath)) match {
      case (false, false) => NotDefinedConfig
      case (true, false) => config.as[FixedReplicasCountConfig]
      case (false, true) => config.as[DividingParallelismConfig]
      case (true, true) => throw new IllegalArgumentException(s"You can specify only one scaling config option: either $fixedReplicasCountPath or $tasksPerReplicaPath")
    }
  }

}