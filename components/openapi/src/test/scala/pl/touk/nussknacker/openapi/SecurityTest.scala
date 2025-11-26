package pl.touk.nussknacker.openapi

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Assertion, BeforeAndAfterAll, LoneElement, TryValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.{Request, Response, SttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.model.{HeaderNames, StatusCode}

import java.util.Base64
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

  private class StubbedOperationLogic[T <: Secret](
      val operationId: String,
      private val path: List[String],
      val securitySchemeName: SecuritySchemeName,
      val expectedSecret: T,
      val checkSecret: (Request[_, _], T) => Assertion
  ) {

    def handleMatchingRequest(request: Request[_, _]): Option[Try[Assertion]] =
      Option(request).filter(requestMatches).map(_ => Try(checkSecret(request, expectedSecret)))

    private def requestMatches(request: Request[_, _]) = {
      request.uri.path == path
    }

  }

  private val stubbedSecretApiKeyCheckingLogics = List[StubbedOperationLogic[ApiKeySecret]](
    new StubbedOperationLogic[ApiKeySecret](
      "headerOperationId",
      "headerPath" :: Nil,
      SecuritySchemeName("headerConfig"),
      ApiKeySecret("h1"),
      (req, expectedSecret) =>
        req.headers.find(_.name == "keyHeader").map(_.value) shouldBe Some(expectedSecret.apiKeyValue)
    ),
    new StubbedOperationLogic[ApiKeySecret](
      "queryOperationId",
      "queryPath" :: Nil,
      SecuritySchemeName("queryConfig"),
      ApiKeySecret("q1"),
      (req, expectedSecret) => req.uri.params.get("keyParam") shouldBe Some(expectedSecret.apiKeyValue)
    ),
    new StubbedOperationLogic[ApiKeySecret](
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

  private val stubbedBasicAuthSecretCheckingLogic = new StubbedOperationLogic[HttpBasicAuthSecret](
    "basicAuthOperationId",
    "basicAuthPath" :: Nil,
    SecuritySchemeName("basicAuthConfig"),
    HttpBasicAuthSecret("u1", "p1"),
    (req, expectedSecret) => {
      val base64UsernamePassword =
        Base64.getEncoder.encodeToString(s"${expectedSecret.username}:${expectedSecret.password}".getBytes)
      req.headers.find(_.name == HeaderNames.Authorization).map(_.value) shouldBe Some(
        s"Basic $base64UsernamePassword"
      )
    }
  )

  private val stubbedSecretCheckingLogics: List[StubbedOperationLogic[_ <: Secret]] =
    stubbedSecretApiKeyCheckingLogics :+ stubbedBasicAuthSecretCheckingLogic

  private val definitionMatchingStubbedLogic                  = "service-security-all-schemes.yml"
  private val definitionMatchingStubbedLogicOnlyApiKeySchemes = "service-security-api-key-schemes.yml"
  private val definitionMatchingStubbedLogicOnlyBasicAuth     = "service-security-basic-auth.yml"

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
        enricherWithCorrectConfig(ServiceName(logic.operationId))
          .invoke(context)
          .futureValue shouldBe TypedMap(Map.empty)
      }
    }

    val enricherWithBadConfig =
      parseToEnrichers(
        definitionMatchingStubbedLogic,
        backend,
        baseConfig.copy(
          security = stubbedSecretCheckingLogics
            .map(l => l.securitySchemeName -> l.expectedSecret)
            .map {
              case (name, ApiKeySecret(_))           => name -> ApiKeySecret("wrong")
              case (name, HttpBasicAuthSecret(_, _)) => name -> HttpBasicAuthSecret("wrongUsername", "wrongPassword")
            }
            .toMap,
        )
      )
    stubbedSecretCheckingLogics.foreach { logic =>
      withClue(logic.operationId) {
        intercept[Exception] {
          enricherWithBadConfig(ServiceName(logic.operationId)).invoke(context).futureValue
        }
      }
    }
  }

  test("common secret configured for any api key scheme") {
    stubbedSecretApiKeyCheckingLogics.foreach { config =>
      withClue(config.operationId) {
        val enricherWithSingleSecurityConfig = parseToEnrichers(
          definitionMatchingStubbedLogicOnlyApiKeySchemes,
          backend,
          baseConfig.copy(secrets = List(config.expectedSecret))
        )
        enricherWithSingleSecurityConfig(ServiceName(config.operationId))
          .invoke(context)
          .futureValue shouldBe TypedMap(Map.empty)
      }
    }
  }

  test("secret configured for basic auth matches any basic auth scheme") {
    withClue(stubbedBasicAuthSecretCheckingLogic.operationId) {
      val enricherWithSingleSecurityConfig = parseToEnrichers(
        definitionMatchingStubbedLogicOnlyBasicAuth,
        backend,
        baseConfig.copy(secrets = List(stubbedBasicAuthSecretCheckingLogic.expectedSecret))
      )
      enricherWithSingleSecurityConfig(ServiceName(stubbedBasicAuthSecretCheckingLogic.operationId))
        .invoke(context)
        .futureValue shouldBe TypedMap(Map.empty)
    }
  }

  test("common secret configured for any scheme with one operation handling multiple api key schemes") {
    val secretMatchesEveryScheme = ApiKeySecret("single-secret")
    val backend = SttpBackendStub.asynchronousFuture.whenAnyRequest
      .thenRespondF { request =>
        Future {
          val operationsLogicResults =
            stubbedSecretApiKeyCheckingLogics
              .map(logic => logic.operationId -> Try(logic.checkSecret(request, secretMatchesEveryScheme)))
              .toMap
          operationsLogicResults.filter(_._2.isSuccess) should have size 1
          Response("{}", StatusCode.Ok)
        }
      }
    val enricherWithSingleSecurityConfig = parseToEnrichers(
      "multiple-schemes-for-single-operation.yml",
      backend,
      baseConfig.copy(secrets = List(secretMatchesEveryScheme))
    )
    enricherWithSingleSecurityConfig(ServiceName("root"))
      .invoke(context)
      .futureValue shouldBe TypedMap(Map.empty)
  }

}
