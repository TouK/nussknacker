package pl.touk.nussknacker.k8s.manager

import akka.Done
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{AvailablePortFinder, ExtremelyPatientScalaFutures, ProcessUtils}
import skuber.Pod.Phase
import skuber.api.client.KubernetesClient
import skuber.json.format._
import skuber.{ConfigMap, Container, ObjectMeta, ObjectResource, Pod, Service, Volume}

import java.io.File
import java.net.Socket
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class K8sTestUtils(k8s: KubernetesClient)
    extends K8sUtils(k8s)
    with Matchers
    with OptionValues
    with ExtremelyPatientScalaFutures
    with LazyLogging {

  val reverseProxyPodRemotePort = 8080

  private val reverseProxyPodName = "reverse-proxy"

  private val reverseProxyConfConfigMapName = "reverse-proxy-conf"

  def execPodWithLogs(
      podName: String,
      command: String,
      end: String => Boolean,
      input: Option[String] = None
  ): String = {
    val closePromise = Promise[Unit]()
    val output       = new mutable.StringBuilder
    val stdoutSink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"$podName exec stdout: $s")
      output.append(s)
      if (end(output.toString())) {
        closePromise.success(())
      }
    }
    val stderrSink: Sink[String, _] = Sink.foreach { s =>
      logger.debug(s"$podName exec stderr: $s")
      output.append(s)
    }
    val inputSource = input.map(Source.single)
    k8s
      .exec(
        podName,
        command.split(" ").toIndexedSeq,
        maybeStdout = Some(stdoutSink),
        maybeStderr = Some(stderrSink),
        maybeStdin = inputSource,
        maybeClose = Some(closePromise)
      )
      .futureValue
    output.toString()
  }

  def withForwardedProxyPod(targetUrl: String)(action: Int => Unit): Unit = {
    withRunningProxyPod(targetUrl) { reverseProxyPod =>
      withPortForwarded(reverseProxyPod, reverseProxyPodRemotePort)(action)
    }
  }

  def withPortForwarded(obj: ObjectResource, remotePort: Int)(action: Int => Unit): Unit = {
    ensureRunningStatus(obj)
    val localPort = AvailablePortFinder.findAvailablePorts(1).head
    val portForwardProcess =
      new ProcessBuilder("kubectl", "port-forward", s"${fixedKind(obj)}/${obj.name}", s"$localPort:$remotePort")
        .directory(new File("/tmp"))
        .start()

    ProcessUtils.destroyProcessEventually(portForwardProcess) {
      val processExitFuture = ProcessUtils.attachLoggingAndReturnWaitingFuture(portForwardProcess)
      ProcessUtils.checkIfFailedInstantly(processExitFuture)
      eventually {
        new Socket("localhost", localPort)
      }
      action(localPort)
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
      case other      => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
    }
  }

  // kind is sometime blank - skuber resources keep kind as a field (not a constant) and looks like sometime it is set to blank string
  private def fixedKind(obj: ObjectResource) = {
    Option(obj.kind.toLowerCase).filterNot(_.isBlank).getOrElse {
      obj match {
        case _: Pod     => "pod"
        case _: Service => "service"
        case other      => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
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
    val pod = Pod(
      reverseProxyPodName,
      Pod.Spec(
        containers = List(
          Container(
            image = "nginx:1.23.1",
            name = "reverse-proxy",
            volumeMounts = List(Volume.Mount(reverseProxyConfConfigMapName, "/etc/nginx/conf.d/"))
          )
        ),
        volumes = List(
          Volume(
            reverseProxyConfConfigMapName,
            Volume.ConfigMapVolumeSource(reverseProxyConfConfigMapName)
          )
        )
      )
    )
    // We set proxy_set_header Connection close to make sure that nginx doesn't cache any connection to upstream server
    val configMap = ConfigMap(
      metadata = ObjectMeta(reverseProxyConfConfigMapName),
      data = Map(
        "nginx.conf" ->
          s"""server {
         |  listen $reverseProxyPodRemotePort;
         |  location / {
         |    proxy_pass $targetUrl;
         |    proxy_set_header Connection close;
         |  }
         |}""".stripMargin
      )
    )
    cleanupReverseProxyPod()
    k8s.create(configMap).futureValue
    k8s.create(pod).futureValue
    pod
  }

  private def cleanupReverseProxyPod(): Unit = {
    Future
      .sequence(
        List(
          deleteIfExists[Pod](reverseProxyPodName),
          deleteIfExists[ConfigMap](reverseProxyConfConfigMapName)
        )
      )
      .futureValue
  }

}
