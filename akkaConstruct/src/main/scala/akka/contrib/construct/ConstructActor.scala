package akka.contrib.construct

import akka.actor.Actor

/**
 * A common generic interface for referring to [[ConstructActor]] or [[ParamsConstructActor]] definitions.
 */
sealed trait BaseConstructActor {

  /**
   * The type of [[Actor]] that this constructs.
   */
  type ActorClass <: Actor
}

/**
 * Defines the mechanism and required types to construct an [[ActorOf]] instance for an Actor.
 *
 * This allows you to peg a constructor argument to a specific actor's [[ConstructActor]] and / or
 * [[ParamsConstructActor]] for defining how to create instances of an ActorRef.
 *
 * Usage:
 * {{{
 *   class MyActor extends Actor { /* ... */ }
 *   object MyActor {
 *     class Construct extends ConstructActor {
 *       override type ActorClass = MyActor
 *       override protected def instance(implicit params: ConstructActorParams[MyActor]): ActorClass = new MyActor
 *     }
 *     object Construct extends Construct
 *   }
 *
 *   class OtherClass(myActor: MyActor.Construct) {
 *     val myActorRef = myActor.actor.ref
 *   }
 * }}}
 *
 * @note while you can inject just an [[ActorOf]] instead of a [[ConstructActor]], it is easier to
 *       manage a [[ConstructActor]] when you need to add the ability to customize that Actor's
 *       constructor arguments.
 *
 *       Without this construct, you could use a function Args => ActorOf[ActorClass], however, any
 *       update to the constructor arguments would require that you duplicate the type parameters
 *       everywhere that it is used. This also causes issues with default arguments, multiple
 *       constructors, and documenting the arguments to those constructors. By pegging these
 *       constructors to a single instance of this class, you can provide a single place to define
 *       and manage these methods.
 */
trait ConstructActor extends BaseConstructActor {

  /**
   * Creates an [[ActorOf]] with the default argument constructor for this [[ActorClass]].
   */
  def actor(implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

object ConstructActor {
  // Convenience types to make arity switching easier without having to switch imports
  type P1 = P1ConstructActor
  type P2 = P2ConstructActor
  type P3 = P3ConstructActor
  type P4 = P4ConstructActor
  type P5 = P5ConstructActor
  type P6 = P6ConstructActor
  type P7 = P7ConstructActor
  type P8 = P8ConstructActor
  type P9 = P9ConstructActor
}

/**
 * A [[ConstructActor]] that takes typed arguments.
 *
 * Usage:
 * {{{
 *   class MyActor(name: String) extends Actor { def receive = ??? }
 *   object MyActor {
 *     class Construct extends P1ConstructActor {
 *       override type ActorClass = MyActor
 *       override type P1 = String
 *       override def actorOf(name: String)(params: ConstructActorParams[ActorClass]): ActorOf[ActorClass] =
 *         ActorOf(new MyActor(name))
 *     }
 *     object Construct extends Construct
 *   }
 *
 *   class OtherClass(myActor: MyActor.Construct) {
 *     val myActorRef = myActor.actorOf("MyActorName").ref
 *   }
 * }}}
 */
sealed trait ParamsConstructActor extends BaseConstructActor {

  /**
   * The type of arguments required to construct an instance.
   */
  type Args
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P1ConstructActor extends ParamsConstructActor {
  final override type Args = P1
  type P1

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1))
   * }}}
   * but where p1 is renamed to something more readable.
   */
  def actorOf(p1: P1)(implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P2ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2)
  type P1; type P2

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2))
   * }}}
   * but where p1 and p2 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2)(implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P3ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3)
  type P1; type P2; type P3

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3))
   * }}}
   * but where p1, p2, and p3 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P4ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4)
  type P1; type P2; type P3; type P4

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3))
   * }}}
   * but where p1, p2, p3, and p4 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P5ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4, P5)
  type P1; type P2; type P3; type P4; type P5

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3, p4, p5))
   * }}}
   * but where p1, p2, p3, p4, and p5 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P6ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4, P5, P6)
  type P1; type P2; type P3; type P4; type P5; type P6

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3, p4, p5, p6))
   * }}}
   * but where p1, p2, p3, p4, p5, and p6 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P7ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4, P5, P6, P7)
  type P1; type P2; type P3; type P4; type P5; type P6; type P7

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3, p4, p5, p6, p7))
   * }}}
   * but where p1, p2, p3, p4, p5, p6, and p7 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P8ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4, P5, P6, P7, P8)
  type P1; type P2; type P3; type P4; type P5; type P6; type P7; type P8

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3, p4, p5, p6, p7, p8))
   * }}}
   * but where p1, p2, p3, p4, p5, p6, p7, and p8 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}

/**
 * See [[ParamsConstructActor]] for explanation and usage.
 */
trait P9ConstructActor extends ParamsConstructActor {
  final override type Args = (P1, P2, P3, P4, P5, P6, P7, P8, P9)
  type P1; type P2; type P3; type P4; type P5; type P6; type P7; type P8; type P9

  /**
   * Override this method to provide better documentation for construct calls to answer.
   * Please rename the arguments and change this comment.
   *
   * Typically, this implementation is just:
   * {{{
   *   ActorOf(new ActorClass(p1, p2, p3, p4, p5, p6, p7, p8, p9))
   * }}}
   * but where p1, p2, p3, p4, p5, p6, p7, p8, and p9 are renamed to something more readable.
   */
  def actorOf(p1: P1, p2: P2, p3: P3, p4: P4, p5: P5, p6: P6, p7: P7, p8: P8, p9: P9)
    (implicit params: ConstructActorContext[ActorClass]): ActorOf[ActorClass]
}
