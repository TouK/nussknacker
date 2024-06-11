package pl.touk.nussknacker

import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}

import java.io.InputStream

class NussknackerAppClient(host: String, port: Int) {

  private val nuHost = HttpHost.create(s"http://$host:$port")

  private val httpClient = {
    val credentialsProvider = {
      val cp = new BasicCredentialsProvider()
      cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("admin", "admin"));
      cp
    }
    HttpClientBuilder
      .create()
      .setDefaultCredentialsProvider(credentialsProvider)
      .build()
  }

  def scenarioFromFile(scenario: InputStream): Unit = {
    createNewScenario("test-scenario", "streaming", "Default", "flink")
    importScenario("test-scenario", scenario)
  }

  private def createNewScenario(
      scenarioName: String,
      processingMode: String,
      category: String,
      engine: String
  ): Unit = {
    val request = {
      val r = new HttpPost("api/processes")
      r.setHeader("Content-Type", "application/json")
      r.setEntity(
        new StringEntity(
          s"""
           |{
           |  "name": "$scenarioName",
           |  "processingMode": "$processingMode",
           |  "category": "$category",
           |  "engineSetupName": "$engine",
           |  "isFragment": false
           |}
           |""".stripMargin
        )
      )
      r
    }
    val response = httpClient.execute(nuHost, request)
    if (response.getStatusLine.getStatusCode != 201) {
      throw new IllegalStateException(s"Cannot create scenario $scenarioName. Response: ${response.getStatusLine}")
    }
  }

  private def importScenario(scenarioName: String, scenarioFile: InputStream): Unit = {
    val request = {
      val r = new HttpPost(s"api/processes/import/$scenarioName")
      r.setEntity(
        MultipartEntityBuilder
          .create()
          .addBinaryBody("process", scenarioFile)
          .build()
      )
      r
    }
    val response = httpClient.execute(nuHost, request)
    if (response.getStatusLine.getStatusCode != 200) {
      throw new IllegalStateException(s"Cannot import scenario $scenarioName. Response: ${response.getStatusLine}")
    }
  }

//  private def saveScenario(scenarioName: String): Unit = {
//    val request = {
//      val r = new HttpPut(s"api/processes/$scenarioName")
//      r.setHeader("Content-Type", "application/json")
//      r.setEntity(new StringEntity(
//        s"""
//           |{
//           |  "name": "$scenarioName",
//           |  "processingMode": "$processingMode",
//           |  "category": "$category",
//           |  "engineSetupName": "$engine",
//           |  "isFragment": false
//           |}
//           |""".stripMargin
//      ))
//      r
//    }
//    val response = httpClient.execute(nuHost, request)
//    if (response.getStatusLine.getStatusCode != 201) {
//      throw new IllegalStateException(s"Cannot create scenario $scenarioName. Response: ${response.getStatusLine}")
//    }
//  }

  def close(): Unit = {
    httpClient.close()
  }

}

// echo "Saving scenario $SCENARIO_NAME"
//  START='{"scenarioGraph":'
//  END=',"comment": ""}'
//  curl -s -o /dev/null -L  -H "$AUTHORIZATION_HEADER" -H 'Accept: application/json, text/plain, */*' -H 'Content-Type: application/json;charset=UTF-8' \
//    --data-raw "${START}${SCENARIO}${END}" -X PUT "$DESIGNER_URL/api/processes/$SCENARIO_NAME"
//
//  echo "Scenario successful created and saved.."
//}
