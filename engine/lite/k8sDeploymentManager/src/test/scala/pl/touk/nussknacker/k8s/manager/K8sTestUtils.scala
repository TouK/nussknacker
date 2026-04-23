package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Framing, Sink, Source}
import org.apache.pekko.util.ByteString
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{AvailablePortFinder, ExtremelyPatientScalaFutures}
import skuber.{ConfigMap, Container, Event, ListResource, ObjectMeta, ObjectResource, Pod, Service, Volume}
import skuber.Pod.{LogQueryParams, Phase}
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.networking.v1.Ingress

import java.net.URI
import scala.collection.mutable
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Try, Using}

class K8sTestUtils(k8s: KubernetesClient)
    extends K8sUtils(k8s)
    with Matchers
    with OptionValues
    with ExtremelyPatientScalaFutures
    with LazyLogging {

  val clusterHost: String = URI.create(k8s.clusterServer).getHost match {
    case "0.0.0.0" => "localhost" // Kafka rejects 0.0.0.0 in advertised listeners
    case other     => other
  }

  val localPortForwardHost = "127.0.0.1"

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
    Using.resource(new K8sPortForwarder(obj, remotePort, localPort, localPortForwardHost).start()) { _ =>
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
      case _: Service =>
      case other      => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
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
            image = "nginx:1.28-alpine",
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

  def printResourcesDetails()(implicit materializer: Materializer): Unit = {
    Try(doPrintResourcesDetails()).failed.foreach { ex =>
      logger.warn("Failure during printResourcesDetails", ex)
    }
  }

  private def doPrintResourcesDetails()(implicit materializer: Materializer): Unit = {
    val pods = k8s.list[ListResource[Pod]]().futureValue.items
    logger.info("pods:\n" + pods.mkString("\n"))
    logger.info("services:\n" + k8s.list[ListResource[Service]]().futureValue.items.mkString("\n"))
    logger.info("deployments:\n" + k8s.list[ListResource[Deployment]]().futureValue.items.mkString("\n"))
    logger.info("ingresses:\n" + k8s.list[ListResource[Ingress]]().futureValue.items.mkString("\n"))
    logger.info("events:\n" + k8s.list[ListResource[Event]]().futureValue.items.mkString("\n"))
    pods.foreach { p =>
      logger.info(s"Printing logs for pod: ${p.name}")
      val logParams = LogQueryParams(
        sinceTime = p.metadata.creationTimestamp,
        follow = Some(false),
      )
      val podLogger = k8s.getPodLogSource(p.name, logParams).flatMap { source =>
        source
          .via(Framing.delimiter(ByteString('\n'), maximumFrameLength = 16384, allowTruncation = true))
          .runForeach { bs =>
            logger.info(s"[pod:${p.name}] ${bs.utf8String}")
          }
      }

      Try(Await.result(podLogger, 5.seconds)).failed.foreach { e =>
        logger.warn(s"Failed to fetch logs for ${p.name}: ${e.getMessage}")
      }
      logger.info(s"Finished printing logs for pod: ${p.name}")
    }
  }

}
