package pl.touk.nussknacker

import better.files._
import com.dimafeng.testcontainers._
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.Suite
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy

import java.io.File
import java.nio.file.Files

// todo: singleton container
trait NuDockerBasedInstallationExample extends ForAllTestContainer { // with WithDockerContainers with LazyLogging {
  this: Suite =>

  val test = new DockerComposeContainer(
    composeFiles = Seq(
      new File("examples/installation/docker-compose.yml"),
      new File(Resource.getUrl("docker-compose.override.yml").toURI)
    ),
    exposedServices = Seq(
      ExposedService("schema-registry", 8081)
    ),
    waitingFor = Some(WaitingForService("nginx", new DockerHealthcheckWaitStrategy)),
  )

  override def container: DockerComposeContainer = test

  override def afterStart(): Unit = {
    val schemaRegistryContainer = test.getContainerByServiceName("schema-registry").get

    val transactionSchema =
      """
        |{
        |  "$schema": "http://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "clientId": { "type": "string" },
        |    "amount": { "type": "integer" },
        |    "isLast": { "type": "boolean", "default": false },
        |    "eventDate": { "type": "integer" }
        |  },
        |  "required": ["clientId", "amount"],
        |  "additionalProperties": false
        |}
        |""".stripMargin

    val espaced = transactionSchema.replaceAll("\"", """\\"""").replaceAll("\n", """\\n""")
    given()
      .when()
      .contentType("application/vnd.schemaregistry.v1+json")
      .body(
        s"""{
           |  "schema": "${espaced}",
           |  "schemaType": "JSON",
           |  "references": []
           |}""".stripMargin
      )
      .post(s"http://localhost:${schemaRegistryContainer.getMappedPort(8081)}/subjects/transactions-value/versions")
      .Then()
      .statusCode(200)

    super.afterStart()
  }

}
