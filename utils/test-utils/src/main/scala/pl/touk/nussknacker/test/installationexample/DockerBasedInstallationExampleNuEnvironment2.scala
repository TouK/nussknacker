//package pl.touk.nussknacker.test.installationexample
//
//import better.files._
//import com.dimafeng.testcontainers._
//import com.typesafe.scalalogging.LazyLogging
//import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
//import org.testcontainers.containers.output.Slf4jLogConsumer
//import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
//import pl.touk.nussknacker.test.containers.ContainerExt._
//import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment2.{JSON, singletonContainer}
//import ujson.Value
//import pl.touk.nussknacker.engine.version.BuildInfo
//
//import java.io.{File => JFile}
//import java.time.Duration
//
//// Before running tests in this module, a fresh docker image should be built from sources and placed in the local
//// registry. If you run tests based on this trait in Intellij Idea and the images is not built, you can do it manually:
//// `bash -c "export NUSSKNACKER_SCALA_VERSION=2.12 && sbt dist/Docker/publishLocal"`
//trait DockerBasedInstallationExampleNuEnvironment extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
//  this: Suite =>
//
//  private val specSetupService = unsafeContainerByServiceName("spec-setup")
//
//  def loadFlinkStreamingScenarioFromResource(scenarioName: String, scenarioJsonFile: File): Unit = {
//    val escapedScenarioJson = scenarioJsonFile.contentAsString().replaceAll("\"", "\\\\\"")
//    specSetupService.executeBash(
//      s"""/app/scripts/utils/nu/load-scenario-from-json.sh "$scenarioName" "$escapedScenarioJson" """
//    )
//  }
//
//  def deployAndWaitForRunningState(scenarioName: String): Unit = {
//    specSetupService.executeBash(
//      s"""/app/scripts/utils/nu/deploy-scenario-and-wait-for-running-state.sh "$scenarioName" """
//    )
//  }
//
//  def sendMessageToKafka(topic: String, message: JSON): Unit = {
//    val escapedMessage = message.render().replaceAll("\"", "\\\\\"")
//    specSetupService.executeBash(s"""/app/scripts/utils/kafka/send-to-topic.sh "$topic" "$escapedMessage" """)
//  }
//
//  def readAllMessagesFromKafka(topic: String): List[JSON] = {
//    specSetupService
//      .executeBashAndReadStdout(s"""/app/scripts/utils/kafka/read-from-topic.sh "$topic" """)
//      .split("\n")
//      .toList
//      .map(ujson.read(_))
//  }
//
//  def purgeKafkaTopic(topic: String): Unit = {
//    specSetupService.executeBash(s"""/app/scripts/utils/kafka/purge-topic.sh "$topic" """)
//  }
//
//  private def unsafeContainerByServiceName(name: String) = singletonContainer
//    .getContainerByServiceName(name)
//    .getOrElse(throw new IllegalStateException(s"'$name' service not available!"))
//
//}
//
//object DockerBasedInstallationExampleNuEnvironment2 extends LazyLogging {
//
//  type JSON = Value
//
//  val singletonContainer: DockerComposeContainer = new DockerComposeContainer(
//    composeFiles = Seq(
//      new JFile("examples/installation/docker-compose.yml"),
//      new JFile(Resource.getUrl("spec-setup/spec-setup.override.yml").toURI),
//      new JFile(Resource.getUrl("spec-setup/batch-nu-designer.override.yml").toURI),
//      new JFile(Resource.getUrl("spec-setup/debuggable-nu-designer.override.yml").toURI)
//    ),
//    env = Map(
//      "NUSSKNACKER_VERSION" -> BuildInfo.version
//    ),
//    logConsumers = Seq(
//      ServiceLogConsumer("spec-setup", new Slf4jLogConsumer(logger.underlying))
//    ),
//    waitingFor = Some(
//      WaitingForService(
//        "spec-setup",
//        new LogMessageWaitStrategy()
//          .withRegEx("^Setup done!.*")
//          .withStartupTimeout(Duration.ofSeconds(120L))
//      )
//    ),
//    // Change to 'true' to enable logging
//    tailChildContainers = false
//  )
//
//  singletonContainer.start()
//
//}
