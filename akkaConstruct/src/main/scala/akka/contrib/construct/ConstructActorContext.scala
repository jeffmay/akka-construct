package akka.contrib.construct

import akka.actor.{Actor, ActorRef, ActorRefFactory}

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
 * Captures the parameters required to create an [[ActorRef]] from an [[ActorOf]].
 */
@implicitNotFound("implicit ActorRefFactory required: if outside of an Actor you need an implicit ActorSystem, " +
    "inside of an actor this will use the implicit ActorContext")
abstract class ConstructActorContext[ActorClass <: Actor] private[construct] (implicit tag: ClassTag[ActorClass]) {

  /**
   * The factory that should be used to construct the [[ActorRef]].
   */
  implicit def factory: ActorRefFactory

  /**
   * The [[ClassTag]] of the actor. Used to create [[ActorOf]] and [[akka.actor.Props]] instances.
   */
  implicit def actorClassTag: ClassTag[ActorClass] = tag

  def constructingActor: Option[ActorRef]
}

object ConstructActorContext {

  def apply[A <: Actor: ClassTag](context: ActorRefFactory, constructingActor: Option[ActorRef]): ConstructActorContext[A] = {
    constructingActor match {
      case Some(self) => new ConstructInsideActor(context, self)
      case None       => new ConstructOutsideActor(context)
    }
  }

  def unapply[A <: Actor](params: ConstructActorContext[A])(implicit tag: ClassTag[A]): Option[(ActorRefFactory, Option[ActorRef])] = {
    if (params.actorClassTag == tag) Some((params.factory, params.constructingActor))
    else None
  }

  implicit def params[A <: Actor: ClassTag](implicit context: ActorRefFactory, self: ActorRef = null): ConstructActorContext[A] = {
    apply(context, Option(self))
  }
}

/**
 * Construct this actor within another actor.
 */
case class ConstructInsideActor[A <: Actor: ClassTag](factory: ActorRefFactory, self: ActorRef)
  extends ConstructActorContext[A] {
  override def constructingActor: Option[ActorRef] = Some(self)
}

/**
 * Construct this actor outside of any actor.
 */
case class ConstructOutsideActor[A <: Actor: ClassTag](factory: ActorRefFactory) extends ConstructActorContext[A] {
  override def constructingActor: Option[ActorRef] = None
}
