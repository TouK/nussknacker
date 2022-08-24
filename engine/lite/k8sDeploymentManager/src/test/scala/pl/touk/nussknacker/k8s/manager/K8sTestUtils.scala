package pl.touk.nussknacker.k8s.manager

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{AvailablePortFinder, ProcessUtils, VeryPatientScalaFutures}
import skuber.Pod.Phase
import skuber.api.client.KubernetesClient
import skuber.json.format._
import skuber.{ConfigMap, Container, ObjectMeta, ObjectResource, Pod, Service, Volume}

import java.io.File
import java.net.Socket
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class K8sTestUtils(k8s: KubernetesClient) extends Matchers with OptionValues with VeryPatientScalaFutures {

  val reverseProxyPodRemotePort = 8080

  private val reverseProxyPodName = "reverse-proxy"

  private val reverseProxyConfConfigMapName = "reverse-proxy-conf"

  def withForwardedProxyPod(targetUrl: String)(action: Int => Unit): Unit = {
    withRunningProxyPod(targetUrl) { reverseProxyPod =>
      withPortForwarded(reverseProxyPod, reverseProxyPodRemotePort)(action)
    }
  }

  def withPortForwarded(obj: ObjectResource, remotePort:Int)(action: Int => Unit): Unit = {
    ensureRunningStatus(obj)
    val localPort = AvailablePortFinder.findAvailablePorts(1).head
    val portForwardProcess = new ProcessBuilder("kubectl", "port-forward", s"${fixedKind(obj)}/${obj.name}", s"$localPort:$remotePort")
      .directory(new File("/tmp"))
      .start()

    ProcessUtils.destroyProcessEventually(portForwardProcess) {
      val processExitFuture = ProcessUtils.attachLoggingAndReturnWaitingFuture(portForwardProcess)
      ProcessUtils.checkIfFailedInstantly(processExitFuture)
      eventually {
        new Socket("localhost", localPort)
      }
      action
    }
  }

  private def ensureRunningStatus(obj: ObjectResource): Unit = {
    obj match {
      case p: Pod =>
        // otherwise there is: unable to forward port because pod is not running. Current status=...
        eventually {
          k8s.get[Pod](p.name).futureValue.status.value.phase.value shouldEqual Phase.Running
        }
      case s: Service =>
      case other => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
    }
  }

  // kind is sometime blank - skuber resources keep kind as a field (not a constant) and looks like sometime it is set to blank string
  private def fixedKind(obj: ObjectResource) = {
    Option(obj.kind.toLowerCase).filterNot(_.isBlank).getOrElse {
      obj match {
        case _: Pod => "pod"
        case _: Service => "service"
        case other => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
      }
    }
  }

  private def withRunningProxyPod(targetUrl: String)(action: Pod => Unit): Unit = {
    try {
      val pod = createReverseProxyPod(targetUrl)
      action(pod)
    } finally {
      cleanupReverseProxyPod()
    }
  }

  private def createReverseProxyPod(targetUrl: String): Pod = {
    val pod = Pod(reverseProxyPodName, Pod.Spec(
      containers = List(Container(
        image = "nginx:1.23.1",
        name = "reverse-proxy",
        volumeMounts = List(Volume.Mount(reverseProxyConfConfigMapName, "/etc/nginx/conf.d/"))
      )),
      volumes = List(Volume(
        reverseProxyConfConfigMapName, Volume.ConfigMapVolumeSource(reverseProxyConfConfigMapName)
      ))
    ))
    val configMap = ConfigMap(metadata = ObjectMeta(reverseProxyConfConfigMapName), data = Map("nginx.conf" ->
      s"""server {
         |  listen $reverseProxyPodRemotePort;
         |  location / {
         |    proxy_pass $targetUrl;
         |  }
         |}""".stripMargin))
    cleanupReverseProxyPod()
    k8s.create(configMap).futureValue
    k8s.create(pod).futureValue
    pod
  }

  private def cleanupReverseProxyPod(): Unit = {
    Future.sequence(List(
      k8s.delete[Pod](reverseProxyPodName),
      k8s.delete[ConfigMap](reverseProxyConfConfigMapName)
    )).futureValue
  }

}