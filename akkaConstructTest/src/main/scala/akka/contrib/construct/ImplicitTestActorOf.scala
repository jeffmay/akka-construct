package akka.contrib.construct

import akka.actor.{Actor, Props}
import akka.contrib.construct.ImplicitTestActorOf._
import akka.testkit.TestActorRef

import scala.language.implicitConversions

/**
 * Provides extension methods for [[ActorOf]] to produce [[TestActorRef]] instances.
 */
trait ImplicitTestActorOf {

  implicit def testActorOfOps[A <: Actor](actorOf: ActorOf[A]): TestActorOfOps[A] = new TestActorOfOps[A](actorOf)
}

object ImplicitTestActorOf extends ImplicitTestActorOf {

  private def newTestActorRef[A <: Actor](actorOf: ActorOf[A], initialize: => A, name: String = null)
    (implicit params: ConstructTestActorContext[A]): TestActorRef[A] = {
    name match {
      case null => TestActorRef[A](actorOf.configure(Props(initialize)(actorOf.actorClassTag)))(params.factory)
      case _    => TestActorRef[A](actorOf.configure(Props(initialize)(actorOf.actorClassTag)), name)(params.factory)
    }
  }

  class TestActorOfOps[A <: Actor](val actorOf: ActorOf[A]) extends AnyVal {

    def testRef(implicit params: ConstructTestActorContext[A]): TestActorRef[A] =
      newTestActorRef[A](actorOf, actorOf.initialize(params))

    def testRef(name: String = null)(implicit params: ConstructTestActorContext[A]): TestActorRef[A] =
      newTestActorRef[A](actorOf, actorOf.initialize(params), name)

    def constructTestRefUsing(init: => A, name: String = null)
      (implicit params: ConstructTestActorContext[A]): TestActorRef[A] =
      newTestActorRef[A](actorOf, init, name)
  }
}


