package akka.contrib.construct

import akka.{ConfigurationException, AkkaException}
import akka.actor._

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * The ActorOf factory is designed to abstract out the construction of actors, so that you can use
 * the dependency injection pattern to make testing easier.
 *
 * It comes with a handy type handle so that you can bind to the generic type in your injection
 * framework to avoid the overloaded type () => ActorRef.
 *
 * There are three flavors of [[ActorOf]] that you require in your constructor:
 *
 * 1. [[ActorOf]]          - The base class of all ActorOfs, so that you can always get one of these.
 *
 * 2. [[SingletonActorOf]] - A subclass of [[ActorOf]] that only ever provides a single instance
 *                           of the [[ActorRef]].
 *
 * Usage:
 *
 * {{{
 *   class MyActor(otherActor: ActorOf[OtherActor]) {
 *
 *     lazy val other: ActorRef = otherActor.create()
 *   }
 * }}}
 *
 * Design goals:
 *
 * 1. Props configuration can be adapted by the dependant actor, but the baseline [[Props]] is always
 *    at least configurable by the [[ActorOf]] when the injection is performed.
 *
 * 2. Users of [[ActorOf]] can optionally upgrade their constructor to take a more specific requirement,
 *    such as needing the ability to provide a custom argument, or needing a singleton instance.
 *
 * 3. Multiple actors can share the same [[ConstructActor]] or [[ParamsConstructActor]] for an actor.
 *
 * 4. Manual calls that require an [[ActorOf]] can always be swapped for a [[SingletonActorOf]].
 *
 * 5. Compiler errors instead of assuming any particular construction pattern.
 */
object ActorOf {

  /**
   * Builds the [[ActorOf]] using the given block of code that constructs a new instance.
   *
   * @note This does not work with anonymous Actors that use mixin composition.
   *       You must create a class that has all of the mixins.
   *
   *       For example:
   *       {{{
   *         ActorOf(new Actor with Stash {})  // won't work
   *
   *         class ActorWithStash extends Actor with Stash {}
   *         ActorOf(new ActorWithStash)  // works
   *       }}}
   */
  def apply[A <: Actor: ClassTag](init: => A): ActorOf[A] = new ActorOf[A](_ => init, identity)

  /**
   * Builds the [[ActorOf]] using the given function that constructs a new instance from
   * some Akka context parameters.
   *
   * @note This does not work with anonymous Actors that use mixin composition.
   *       You must create a class that has all of the mixins.
   *
   *       For example:
   *       {{{
   *         ActorOf(new Actor with Stash {})  // won't work
   *
   *         class ActorWithStash extends Actor with Stash {}
   *         ActorOf(new ActorWithStash)  // works
   *       }}}
   */
  def apply[A <: Actor: ClassTag](init: ConstructActorContext[A] => A): ActorOf[A] =
    new ActorOf[A](init, identity)

  /**
   * Create a singleton instance of the given [[ActorOf]].
   */
  implicit def singleton[A <: Actor](of: ActorOf[A]): SingletonActorOf[A] = of.singleton

}

/**
 * A common base class for [[ActorOf]] and [[SingletonActorOf]].
 *
 * @tparam A the specific Actor class
 */
class ActorOf[A <: Actor: ClassTag] private[akka] (
  protected[akka] val initialize: ConstructActorContext[A] => A,
  protected[akka] val configure: Props => Props) {

  /**
   * The [[ClassTag]] of the Actor for building props.
   */
  protected[akka] lazy val actorClassTag: ClassTag[A] = implicitly

  /**
   * Creates the actor instance as a child of the current context.
   */
  protected def actorOf(factory: ActorRefFactory, initialize: => A, name: String = null): ActorRef = {
    name match {
      case null => factory.actorOf(configure(Props(initialize)(actorClassTag)))
      case _    => factory.actorOf(configure(Props(initialize)(actorClassTag)), name)
    }
  }

  /**
   * Construct an instance of the actor OR get the singleton actor.
   *
   * @param params the Akka specific context in which this [[ActorRef]] is to be created.
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  def ref(implicit params: ConstructActorContext[A]): ActorRef = actorOf(params.factory, initialize(params))

  /**
   * Construct an instance of the actor
   *
   * @param name the unique name for the [[ActorRef]].
   * @param params the Akka specific context in which this [[ActorRef]] is to be created.
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def ref(name: String)(implicit params: ConstructActorContext[A]): ActorRef = actorOf(params.factory, initialize(params), name)

  /**
   * Create a singleton version of this [[ActorOf]].
   *
   * @note if you need an instance of this in the dependent class, you should be able to require it in
   *       the constructor.
   */
  def singleton: SingletonActorOf[A] = new ActorOf[A](initialize, configure) with SingletonActorOf[A]

  /**
   * Defines a mechanism to customize for the [[ActorOf]]'s props config function.
   *
   * @param update a function used to update the current [[Props]]
   * @return a regular [[ActorOf]] to avoid leaking the configuration method in the result
   */
  def withPropsConfig(update: Props => Props): ActorOf[A] =
    new ActorOf[A](this.initialize, update)(actorClassTag)

  /**
   * Defines a mechanism to customize for the [[ActorOf]]'s props config function.
   *
   * @param replacement replaces the current props config function
   * @return a regular [[ActorOf]] to avoid leaking the configuration method in the result
   */
  def withPropsConfig(replacement: Props): ActorOf[A] =
    new ActorOf[A](this.initialize, _ => replacement)(actorClassTag)
}

/**
 * Defines a mechanism for creating a singleton [[ActorRef]] that is returned every time the actor is asked for.
 *
 * @note this is not like [[Props]] in that it is not serializable and it cannot be shared across actor systems.
 *
 * @tparam A the specific Actor class
 */
trait SingletonActorOf[A <: Actor] extends ActorOf[A] {

  /**
   * The singleton instance of the Actor.
   */
  private[this] var actor: ActorRef = null

  /**
   * Sets / returns the singleton actor
   */
  override protected def actorOf(factory: ActorRefFactory, initialize: => A, name: String = null): ActorRef = {
    if (actor ne null) {
      if (name eq null) actor
      else throw new SingletonActorAlreadyInitialized(actor)
    }
    else synchronized {
      if (actor eq null) {
        actor = super.actorOf(factory, initialize, name)
      }
      actor
    }
  }
}

/**
 * Thrown when a [[SingletonActorOf]] is asked for a custom ref after the [[ActorRef]] has been initialized.
 *
 * @param ref the already initialized ref
 */
class SingletonActorAlreadyInitialized(ref: ActorRef)
  extends AkkaException(
    s"Cannot initialize a SingletonActorOf more than once. $ref is already initialized. " +
      "Try storing the ActorRef in a value.")
