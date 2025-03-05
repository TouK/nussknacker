package pl.touk.nussknacker.openapi

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Assertion, BeforeAndAfterAll, LoneElement, TryValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ContextId
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.{Request, Response, SttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.model.{HeaderNames, StatusCode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class SecurityTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with TryValues
    with LoneElement
    with LazyLogging
    with PatientScalaFutures
    with BaseOpenAPITest {

  private class StubbedOperationLogic(
      val operationId: String,
      private val path: List[String],
      val securitySchemeName: SecuritySchemeName,
      val expectedSecret: ApiKeySecret,
      val checkSecret: (Request[_, _], ApiKeySecret) => Assertion
  ) {

    def handleMatchingRequest(request: Request[_, _]): Option[Try[Assertion]] =
      Option(request).filter(requestMatches).map(_ => Try(checkSecret(request, expectedSecret)))

    private def requestMatches(request: Request[_, _]) = {
      request.uri.path == path
    }

  }

  private val stubbedSecretCheckingLogics = List[StubbedOperationLogic](
    new StubbedOperationLogic(
      "headerOperationId",
      "headerPath" :: Nil,
      SecuritySchemeName("headerConfig"),
      ApiKeySecret("h1"),
      (req, expectedSecret) =>
        req.headers.find(_.name == "keyHeader").map(_.value) shouldBe Some(expectedSecret.apiKeyValue)
    ),
    new StubbedOperationLogic(
      "queryOperationId",
      "queryPath" :: Nil,
      SecuritySchemeName("queryConfig"),
      ApiKeySecret("q1"),
      (req, expectedSecret) => req.uri.params.get("keyParam") shouldBe Some(expectedSecret.apiKeyValue)
    ),
    new StubbedOperationLogic(
      "cookieOperationId",
      "cookiePath" :: Nil,
      SecuritySchemeName("cookieConfig"),
      ApiKeySecret("c1"),
      (req, expectedSecret) =>
        req.headers.find(_.name == HeaderNames.Cookie).map(_.value) shouldBe Some(
          s"keyCookie=${expectedSecret.apiKeyValue}"
        )
    ),
  )

  private val definitionMatchingStubbedLogic = "service-security.yml"

  val backend: SttpBackend[Future, Any] = SttpBackendStub.asynchronousFuture.whenAnyRequest
    .thenRespondF { request =>
      Future {
        val operationsLogicResults =
          stubbedSecretCheckingLogics
            .flatMap(logic => logic.handleMatchingRequest(request).map(logic.operationId -> _))
            .toMap
        operationsLogicResults.loneElement._2.success.value
        Response("{}", StatusCode.Ok)
      }
    }

  test("secret configured for each scheme in definition") {
    val enricherWithCorrectConfig =
      parseToEnrichers(
        definitionMatchingStubbedLogic,
        backend,
        baseConfig.copy(
          security = stubbedSecretCheckingLogics.map(c => c.securitySchemeName -> c.expectedSecret).toMap,
        )
      )
    stubbedSecretCheckingLogics.foreach { logic =>
      withClue(logic.operationId) {
        implicit val contextId: ContextId = ContextId("1")
        enricherWithCorrectConfig(ServiceName(logic.operationId))
          .invoke(Map.empty)
          .futureValue shouldBe TypedMap(Map.empty)
      }
    }

    val enricherWithBadConfig =
      parseToEnrichers(
        definitionMatchingStubbedLogic,
        backend,
        baseConfig.copy(
          security = stubbedSecretCheckingLogics.map(c => c.securitySchemeName -> ApiKeySecret("bla")).toMap,
        )
      )
    stubbedSecretCheckingLogics.foreach { logic =>
      withClue(logic.operationId) {
        intercept[Exception] {
          implicit val contextId: ContextId = ContextId("1")
          enricherWithBadConfig(ServiceName(logic.operationId)).invoke(Map.empty).futureValue
        }
      }
    }
  }

  test("common secret configured for any scheme") {
    stubbedSecretCheckingLogics.foreach { config =>
      withClue(config.operationId) {
        val enricherWithSingleSecurityConfig = parseToEnrichers(
          definitionMatchingStubbedLogic,
          backend,
          baseConfig.copy(secret = Some(config.expectedSecret))
        )
        implicit val contextId: ContextId = ContextId("1")
        enricherWithSingleSecurityConfig(ServiceName(config.operationId))
          .invoke(Map.empty)
          .futureValue shouldBe TypedMap(Map.empty)
      }
    }
  }

  test("common secret configured for any scheme with one operation handling multiple security schemes") {
    val secretMatchesEveryScheme = ApiKeySecret("single-secret")
    val backend = SttpBackendStub.asynchronousFuture.whenAnyRequest
      .thenRespondF { request =>
        Future {
          val operationsLogicResults =
            stubbedSecretCheckingLogics
              .map(logic => logic.operationId -> Try(logic.checkSecret(request, secretMatchesEveryScheme)))
              .toMap
          operationsLogicResults.filter(_._2.isSuccess) should have size 1
          Response("{}", StatusCode.Ok)
        }
      }
    val enricherWithSingleSecurityConfig = parseToEnrichers(
      "multiple-schemes-for-single-operation.yml",
      backend,
      baseConfig.copy(secret = Some(secretMatchesEveryScheme))
    )
    implicit val contextId: ContextId = ContextId("1")
    enricherWithSingleSecurityConfig(ServiceName("root"))
      .invoke(Map.empty)
      .futureValue shouldBe TypedMap(Map.empty)
  }

}
