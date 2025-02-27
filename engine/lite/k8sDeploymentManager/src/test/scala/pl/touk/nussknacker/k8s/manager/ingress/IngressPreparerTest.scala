package pl.touk.nussknacker.k8s.manager.ingress

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromIterable, fromMap}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import skuber.ObjectMeta
import skuber.networking.v1.Ingress

import scala.jdk.CollectionConverters._

class IngressPreparerTest extends AnyFunSuite {

  val processVersion: ProcessVersion =
    ProcessVersion(VersionId(10), ProcessName("some-name"), ProcessId(4), List.empty, "user", None)
  val nussknackerInstanceName = "foo-release"

  val serviceName                               = "my-service"
  val servicePort                               = 80
  val liteStreamMetaData: TypeSpecificData      = LiteStreamMetaData()
  val slug                                      = "my-slug"
  val requestResponseMetaData: TypeSpecificData = RequestResponseMetaData(Some(slug))
  val hostname                                  = "my.host"

  test("should prepare ingress") {
    val preparer = new IngressPreparer(
      config = IngressConfig(enabled = true, Some(hostname)),
      nuInstanceName = Some(nussknackerInstanceName)
    )

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort) shouldBe Some(
      Ingress(
        metadata = ObjectMeta(
          name = "foo-release-scenario-4-some-name",
          labels = Map(
            "nussknacker.io/scenarioName"            -> "some-name-59107c750f",
            "nussknacker.io/scenarioId"              -> "4",
            "nussknacker.io/scenarioVersion"         -> "10",
            "nussknacker.io/nussknackerInstanceName" -> "foo-release"
          ),
          annotations = Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2")
        ),
        spec = Some(
          Ingress.Spec(
            rules = List(
              Ingress.Rule(
                host = Some(hostname),
                http = Ingress.HttpRule(
                  List(
                    Ingress.Path(
                      path = "/my-slug(/|$)(.*)",
                      backend = Ingress.Backend(service =
                        Some(Ingress.ServiceType(serviceName, Ingress.Port(number = Some(servicePort))))
                      ),
                      pathType = Ingress.PathType.Prefix
                    )
                  )
                )
              )
            ),
            tls = Nil
          )
        )
      )
    )
  }

  test("should prepare ingress with tls (and no 'nussknackerInstanceName')") {
    val tlsSecret = "my-secret"
    val tlsConf = ConfigFactory
      .empty()
      .withValue(
        "spec.tls",
        fromIterable(
          List(
            fromMap(
              Map(
                "hosts"      -> fromIterable(List(hostname).asJava),
                "secretName" -> tlsSecret
              ).asJava
            )
          ).asJava
        )
      )

    val preparer =
      new IngressPreparer(config = IngressConfig(enabled = true, Some(hostname), "/", tlsConf), nuInstanceName = None)

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort) shouldBe Some(
      Ingress(
        metadata = ObjectMeta(
          name = "scenario-4-some-name",
          labels = Map(
            "nussknacker.io/scenarioName"    -> "some-name-59107c750f",
            "nussknacker.io/scenarioId"      -> "4",
            "nussknacker.io/scenarioVersion" -> "10"
          ),
          annotations = Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2")
        ),
        spec = Some(
          Ingress.Spec(
            rules = List(
              Ingress.Rule(
                host = Some(hostname),
                http = Ingress.HttpRule(
                  List(
                    Ingress.Path(
                      path = "/my-slug(/|$)(.*)",
                      backend = Ingress.Backend(service =
                        Some(Ingress.ServiceType(serviceName, Ingress.Port(number = Some(servicePort))))
                      ),
                      pathType = Ingress.PathType.Prefix
                    )
                  )
                )
              )
            ),
            tls = List(
              Ingress.TLS(
                hosts = List(hostname),
                Some(tlsSecret)
              )
            )
          )
        )
      )
    )
  }

  test("should prepare ingress with custom annotations") {
    val annotationConf = ConfigFactory
      .empty()
      .withValue(
        "metadata.annotations",
        fromMap(
          Map(
            "my-annotation/touk" -> "abc"
          ).asJava
        )
      )

    val preparer = new IngressPreparer(
      config = IngressConfig(enabled = true, Some(hostname), "/", annotationConf),
      nuInstanceName = None
    )

    preparer
      .prepare(processVersion, requestResponseMetaData, serviceName, servicePort)
      .map(_.metadata.annotations) shouldBe Some(
      Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2", "my-annotation/touk" -> "abc")
    )
  }

  test("should not prepare ingress for lite-streaming") {
    val preparer = new IngressPreparer(config = IngressConfig(enabled = true, Some(hostname)), nuInstanceName = None)

    preparer.prepare(processVersion, liteStreamMetaData, serviceName, servicePort) shouldBe None
  }

  test("should not prepare ingress when disabled") {
    val preparer = new IngressPreparer(config = IngressConfig(enabled = false, Some(hostname)), nuInstanceName = None)

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort) shouldBe None
  }

}
