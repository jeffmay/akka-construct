package akka.contrib.construct

import akka.actor.{ActorRef, ActorRefFactory}

sealed trait ConstructActorParams {

  def context: ActorRefFactory

  def constructingActor: Option[ActorRef]
}

case class ConstructInsideActor(context: ActorRefFactory, self: ActorRef) extends ConstructActorParams {
  override def constructingActor: Option[ActorRef] = Some(self)
}

case class ConstructOutsideActor(context: ActorRefFactory) extends ConstructActorParams {
  override def constructingActor: Option[ActorRef] = None
}

object ConstructActorParams {

  def apply(context: ActorRefFactory, constructingActor: Option[ActorRef]): ConstructActorParams = {
    constructingActor match {
      case Some(self) => new ConstructInsideActor(context, self)
      case None       => new ConstructOutsideActor(context)
    }
  }

  def unapply(params: ConstructActorParams): Option[(ActorRefFactory, Option[ActorRef])] = {
    Some((params.context, params.constructingActor))
  }

  implicit def params(implicit context: ActorRefFactory, self: ActorRef = null): ConstructActorParams = {
    ConstructActorParams(context, Option(self))
  }
}
