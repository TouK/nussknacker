package pl.touk.nussknacker.ui.util

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}

/**
  * Failure of child actor cause sending message with Failure to last sender.
  * It is very experimental - lastSender can be other than real sender.
  */
class ReplyingToSenderSupervisorActor(childProps: Props, childName: String) extends Actor {

  var child: Option[ActorRef] = None
  var lastSender: Option[ActorRef] = None

  override def preStart(): Unit = startChild()

  def startChild(): Unit = {
    if (child.isEmpty) {
      child = Some(context.watch(context.actorOf(childProps, childName)))
    }
  }

  override def receive: Receive = {
    case msg if child.contains(sender()) =>
      context.parent ! msg

    case msg => child match {
      case Some(c) ⇒
        lastSender = Some(sender())
        c.forward(msg)
      case None    ⇒ context.system.deadLetters.forward(msg)
    }
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy()({
    case ex: Throwable =>
      lastSender.foreach(_ ! Failure(ex))
      SupervisorStrategy.defaultDecider.applyOrElse(ex, (_: Any) => SupervisorStrategy.Escalate)
  })

}

object ReplyingToSenderSupervisorActor {

  def props(childProps: Props, childName: String) = Props(new ReplyingToSenderSupervisorActor(childProps, childName))

}
