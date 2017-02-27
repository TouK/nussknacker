package pl.touk.esp.engine.standalone.http

import java.io.File
import java.net.URLClassLoader

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.DeploymentData
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.standalone.management.{DeploymentService, EspError}
import pl.touk.esp.engine.util.ThreadUtils

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging {

  implicit val system = ActorSystem("esp-standalone-http")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val processesClassLoader = loadProcessesClassloader(config)
  val creator = loadCreator(config)

  val deploymentService = new DeploymentService(creator, config)

  import argonaut.ArgonautShapeless._

  def main(args: Array[String]): Unit = {
    val ports = for {
      mgmPort <- Try(args(0).toInt).toOption
      processesPort <- Try(args(1).toInt).toOption
    } yield (mgmPort, processesPort)
    ports match {
      case Some((mgmPort, procPort)) => initHttp(mgmPort, procPort)
      case None => initHttp()
    }
  }

  def initHttp(managementPort: Int = 8070, processesPort: Int = 8080) = {
    Http().bindAndHandle(
      managementRoute,
      interface = "0.0.0.0",
      port = managementPort
    )

    Http().bindAndHandle(
      processRoute,
      interface = "0.0.0.0",
      port = processesPort
    )

  }

  val managementRoute: Route = ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
    path("deploy") {
      post {
        entity(as[DeploymentData]) { data =>
          complete {
            toResponse(deploymentService.deploy(data.processId, data.processJson))
          }
        }
      }
    } ~ path("checkStatus" / Segment) { processId =>
      get {
        complete {
          deploymentService.checkStatus(processId) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    } ~ path("cancel" / Segment) { processId =>
      post {
        complete {
          deploymentService.cancel(processId) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    }
  }

  val processRoute: Route = ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
    path(Segment) { processId =>
      post {
        entity(as[Array[Byte]]) { bytes =>
          val interpreter = deploymentService.getInterpreter(processId)
          interpreter match {
            case None =>
              complete {
                HttpResponse(status = StatusCodes.NotFound)
              }
            case Some(processInterpreter) =>
              val input = processInterpreter.source[Any].toObject(bytes)
              complete {
                processInterpreter.run(input).map(_.map(_.asInstanceOf[String]))
              }
          }
        }
      }
    }
  }

  def toResponse(xor: Xor[EspError, Unit]): ToResponseMarshallable =
    xor match {
      case Xor.Right(unit) =>
        unit
      case Xor.Left(error) =>
        HttpResponse(status = StatusCodes.BadRequest, entity = error.message)
    }

  def loadCreator(config: Config): ProcessConfigCreator = {
    ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
      ThreadUtils.loadUsingContextLoader(config.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]
    }
  }

  def loadProcessesClassloader(config: Config): ClassLoader = {
    if (!config.hasPath("jarPath")) { //to troche slabe, ale na razie chcemy jakos testowac bez jara...
      getClass.getClassLoader
    } else {
      val jarFile = new File(config.getString("jarPath"))
      val classLoader = new URLClassLoader(Array(jarFile.toURI.toURL), getClass.getClassLoader)
      classLoader
    }
  }

}