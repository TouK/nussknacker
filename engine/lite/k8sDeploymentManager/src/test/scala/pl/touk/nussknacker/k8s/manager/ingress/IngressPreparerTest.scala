package pl.touk.nussknacker.k8s.manager.ingress

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData, TypeSpecificData}
import skuber.ObjectMeta
import skuber.networking.v1.Ingress

class IngressPreparerTest extends AnyFunSuite {

  val processVersion: ProcessVersion = ProcessVersion(VersionId(10), ProcessName("some-name"), ProcessId(4), "user", None)
  val nussknackerInstanceName = "foo-release"

  val serviceName = "my-service"
  val servicePort = 80
  val liteStreamMetaData: TypeSpecificData = LiteStreamMetaData()
  val slug = "my-slug"
  val requestResponseMetaData: TypeSpecificData = RequestResponseMetaData(Some(slug))
  val hostname = "my.host"

  test("should prepare ingress") {
    val preparer = new IngressPreparer(config = IngressConfig(hostname), nuInstanceName = Some(nussknackerInstanceName))

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort) shouldBe Some(Ingress(
      metadata = ObjectMeta(
        name = "foo-release-scenario-some-name",
        labels = Map(
          "nussknacker.io/scenarioName"-> "some-name-59107c750f",
          "nussknacker.io/scenarioId" -> "4",
          "nussknacker.io/scenarioVersion" -> "10",
          "nussknacker.io/nussknackerInstanceName" -> "foo-release"
        ),
        annotations = Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2")
      ),
      spec = Some(Ingress.Spec(
        rules = List(
          Ingress.Rule(host = Some(hostname), http = Ingress.HttpRule(List(
            Ingress.Path(path = "/my-slug(/|$)(.*)", backend = Ingress.Backend(service = Some(Ingress.ServiceType(serviceName, Ingress.Port(number = Some(servicePort))))), pathType = Ingress.PathType.Prefix)
          )))),
        tls = Nil
      ))
    ))
  }

  test("should prepare ingress with tls") {
    val tlsSecret = "my-secret"
    val preparer = new IngressPreparer(config = IngressConfig(hostname, Some(IngressTlsConfig(enabled = true, Some(tlsSecret)))), nuInstanceName = None)

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort).flatMap(_.spec).flatMap(_.tls.headOption) shouldBe Some(Ingress.TLS(
      hosts = List(hostname),
      Some(tlsSecret)
    ))
  }

  test("should prepare ingress without tls when disabled") {
    val tlsSecret = "my-secret"
    val preparer = new IngressPreparer(config = IngressConfig(hostname, Some(IngressTlsConfig(enabled = false, Some(tlsSecret)))), nuInstanceName = None)

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort).flatMap(_.spec).flatMap(_.tls.headOption) shouldBe None
  }

  test("should prepare ingress with custom annotations") {
    val preparer = new IngressPreparer(config = IngressConfig(hostname, annotations = Map("my-annotation/touk" ->"abc")), nuInstanceName = None)

    preparer.prepare(processVersion, requestResponseMetaData, serviceName, servicePort).map(_.metadata.annotations) shouldBe Some(
      Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2", "my-annotation/touk" ->"abc")
    )
  }

  test("should not prepare ingress for lite-streaming") {
    val preparer = new IngressPreparer(config = IngressConfig("my.host"), nuInstanceName = None)

    preparer.prepare(processVersion, liteStreamMetaData, serviceName, servicePort) shouldBe None
  }

}
