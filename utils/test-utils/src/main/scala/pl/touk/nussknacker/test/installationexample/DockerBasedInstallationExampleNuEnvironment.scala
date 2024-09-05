//package pl.touk.nussknacker.test.installationexample
//
//import com.dimafeng.testcontainers.{DockerComposeContainer, ServiceLogConsumer, WaitingForService}
//import org.testcontainers.containers.output.Slf4jLogConsumer
//import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
//import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment2.logger
//
//import java.io.{File => JFile}
//import java.time.Duration
//class DockerBasedInstallationExampleNuEnvironment(specTweaks: Iterable[JFile],
//                                                  showLogs: Boolean = false)
//  extends DockerComposeContainer(
//    composeFiles = new JFile("examples/installation/docker-compose.yml") :: specTweaks.toList,
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
//    tailChildContainers = showLogs
//  ) {
//
//}
