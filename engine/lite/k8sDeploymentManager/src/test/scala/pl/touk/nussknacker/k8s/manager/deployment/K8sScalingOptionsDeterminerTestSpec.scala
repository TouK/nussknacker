package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus.toFicusConfig
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig._

class K8sScalingOptionsDeterminerTestSpec extends FunSuite with Matchers {

  test("round dividing") {
    val determiner = new DividingParallelismK8sScalingOptionsDeterminer(DividingParallelismConfig(tasksPerReplica = 4))
    determiner.determine(parallelism = 8) shouldEqual K8sScalingOptions(replicasCount = 2, noOfTasksInReplica = 4)
  }

  test("dividing with remainder") {
    val determiner = new DividingParallelismK8sScalingOptionsDeterminer(DividingParallelismConfig(tasksPerReplica = 4))
    determiner.determine(parallelism = 9) shouldEqual K8sScalingOptions(replicasCount = 3, noOfTasksInReplica = 3)
    determiner.determine(parallelism = 10) shouldEqual K8sScalingOptions(replicasCount = 3, noOfTasksInReplica = 4)
  }

  test("fixed replicas round dividing") {
    val determiner = new FixedReplicasCountK8sScalingOptionsDeterminer(FixedReplicasCountConfig(fixedReplicasCount = 2))
    determiner.determine(parallelism = 8) shouldEqual K8sScalingOptions(replicasCount = 2, noOfTasksInReplica = 4)
  }

  test("fixed replicas dividing with remainder") {
    val determiner = new FixedReplicasCountK8sScalingOptionsDeterminer(FixedReplicasCountConfig(fixedReplicasCount = 2))
    determiner.determine(parallelism = 9) shouldEqual K8sScalingOptions(replicasCount = 2, noOfTasksInReplica = 5)
  }

  test("configuration reading") {
    val emptyConfig = ConfigFactory.empty()
    emptyConfig.as[K8sScalingConfig] shouldEqual NotDefinedConfig

    val configWithFixedReplicasCount = ConfigFactory.parseString(
      s"""
         |$fixedReplicasCountPath: 2
         |""".stripMargin)
    configWithFixedReplicasCount.as[K8sScalingConfig] shouldEqual FixedReplicasCountConfig(2)


    val configWithTasksPerReplica = ConfigFactory.parseString(
      s"""
         |$tasksPerReplicaPath: 4
         |""".stripMargin)
    configWithTasksPerReplica.as[K8sScalingConfig] shouldEqual DividingParallelismConfig(4)

    val configWithMultipleStrategies = ConfigFactory.parseString(
      s"""
         |$fixedReplicasCountPath: 2
         |$tasksPerReplicaPath: 4
         |""".stripMargin)

    an[IllegalArgumentException] shouldBe thrownBy {
      configWithMultipleStrategies.as[K8sScalingConfig]
    }
  }

}
