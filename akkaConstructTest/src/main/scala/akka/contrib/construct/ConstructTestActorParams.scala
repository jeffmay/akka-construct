package akka.contrib.construct

import akka.actor.{Actor, ActorRef, ActorSystem}

sealed trait ConstructTestActorParams {

  def context: ActorSystem

  def constructingActor: Option[ActorRef]

  implicit def toConstructActorParams: ConstructActorParams = ConstructActorParams(context, constructingActor)
}

case class ConstructInsideTestActor(context: ActorSystem, self: ActorRef)
  extends ConstructTestActorParams {
  override def constructingActor: Option[ActorRef] = Some(self)
}

case class ConstructOutsideTestActor(context: ActorSystem)
  extends ConstructTestActorParams {
  override def constructingActor: Option[ActorRef] = None
}

object ConstructTestActorParams {

  def apply(context: ActorSystem, constructingActor: Option[ActorRef]): ConstructTestActorParams = {
    constructingActor match {
      case Some(self) => new ConstructInsideTestActor(context, self)
      case None       => new ConstructOutsideTestActor(context)
    }
  }

  def unapply[T <: Actor](params: ConstructTestActorParams): Option[(ActorSystem, Option[ActorRef])] = {
    params match {
      case ConstructInsideTestActor(context, self) => Some((context, Some(self)))
      case ConstructOutsideTestActor(context)      => Some((context, None))
    }
  }

  implicit def params(implicit context: ActorSystem, self: ActorRef = null): ConstructTestActorParams = {
    ConstructTestActorParams(context, Option(self))
  }
}
