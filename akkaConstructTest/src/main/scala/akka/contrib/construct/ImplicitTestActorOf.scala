package akka.contrib.construct

import akka.actor.{Actor, Props}
import akka.contrib.construct.ImplicitTestActorOf._
import akka.testkit.TestActorRef

import scala.language.implicitConversions

trait ImplicitTestActorOf {

  implicit def testActorOfOps[A <: Actor](actorOf: ActorOf[A]): TestActorOfOps[A] = new TestActorOfOps[A](actorOf)

  implicit def testCustomActorOfOps[A <: Actor, P](actorOf: CustomActorOf[A, P]): TestCustomActorOfOps[A, P] =
    new TestCustomActorOfOps[A, P](actorOf)
}

object ImplicitTestActorOf extends ImplicitTestActorOf {

  private def newTestActorRef[A <: Actor](actorOf: ActorOf[A], initialize: => A, name: String = null)
    (implicit params: ConstructTestActorParams): TestActorRef[A] = {
    name match {
      case null => TestActorRef[A](actorOf.configure(Props(initialize)(actorOf.actorClassTag)))(params.context)
      case _    => TestActorRef[A](actorOf.configure(Props(initialize)(actorOf.actorClassTag)), name)(params.context)
    }
  }

  class TestActorOfOps[A <: Actor](val actorOf: ActorOf[A]) extends AnyVal {

    def testRef(implicit params: ConstructTestActorParams): TestActorRef[A] =
      newTestActorRef[A](actorOf, actorOf.initialize())

    def testRef(name: String = null)(implicit params: ConstructTestActorParams): TestActorRef[A] =
      newTestActorRef[A](actorOf, actorOf.initialize(), name)

    def constructTestRefUsing(init: => A, name: String = null)
      (implicit params: ConstructTestActorParams): TestActorRef[A] =
      newTestActorRef[A](actorOf, init, name)
  }

  class TestCustomActorOfOps[A <: Actor, P](val actorOf: CustomActorOf[A, P]) extends AnyVal {

    def constructTestRef(args: => P = actorOf.defaultArgs(), name: String = null)
      (implicit params: ConstructTestActorParams): TestActorRef[A] =
      newTestActorRef[A](actorOf, actorOf.defaultConstructor(args))

    def constructTestRefUsing(constructor: P => A, args: => P = actorOf.defaultArgs(), name: String = null)
      (implicit params: ConstructTestActorParams): TestActorRef[A] =
      newTestActorRef[A](actorOf, constructor(args), name)
  }
}


