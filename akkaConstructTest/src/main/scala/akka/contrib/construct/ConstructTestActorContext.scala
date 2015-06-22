package akka.contrib.construct

import akka.actor.{ActorContext, Actor, ActorRef, ActorSystem}

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
 * Extends [[ConstructActorContext]] to require an [[ActorSystem]] for [[akka.testkit.TestActorRef]]s
 * to use. Instead of the generic [[akka.actor.ActorRefFactory]].
 */
@implicitNotFound("implicit ActorSystem required: if outside of an Actor you need an implicit ActorSystem, " +
  "inside of an actor this will use the implicit ActorContext.system")
abstract class ConstructTestActorContext[ActorClass <: Actor] private[construct] (implicit tag: ClassTag[ActorClass])
  extends ConstructActorContext[ActorClass] {

  override implicit def factory: ActorSystem
}

object ConstructTestActorContext {

  def apply[A <: Actor: ClassTag](context: ActorSystem, constructingActor: Option[ActorRef]): ConstructTestActorContext[A] = {
    constructingActor match {
      case Some(self) => new ConstructTestInsideActor[A](context, self)
      case None       => new ConstructTestOutsideActor[A](context)
    }
  }

  def unapply[A <: Actor](params: ConstructTestActorContext[A])(implicit tag: ClassTag[A]): Option[(ActorSystem, Option[ActorRef])] = {
    params match {
      case wrong if wrong.actorClassTag != tag     => None
      case ConstructTestInsideActor(context, self) => Some((context, Some(self)))
      case ConstructTestOutsideActor(context)      => Some((context, None))
    }
  }

  implicit def constructInside[A <: Actor: ClassTag](implicit context: ActorContext, self: ActorRef = null): ConstructTestActorContext[A] = {
    ConstructTestActorContext(context.system, Option(self))
  }

  implicit def constructOutside[A <: Actor: ClassTag](implicit context: ActorSystem, self: ActorRef = null): ConstructTestActorContext[A] = {
    ConstructTestActorContext(context, Option(self))
  }
}

case class ConstructTestInsideActor[A <: Actor: ClassTag](factory: ActorSystem, self: ActorRef)
  extends ConstructTestActorContext[A] {
  override def constructingActor: Option[ActorRef] = Some(self)
}

case class ConstructTestOutsideActor[A <: Actor: ClassTag](factory: ActorSystem)
  extends ConstructTestActorContext[A] {
  override def constructingActor: Option[ActorRef] = None
}
