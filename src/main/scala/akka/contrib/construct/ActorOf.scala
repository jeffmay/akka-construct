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
 * 2. [[CustomActorOf]]    - Allows the dependent Actor to provide custom arguments for constructing an
 *                           instance of the Actor. This can never also be a [[SingletonActorOf]]
 *
 * 3. [[SingletonActorOf]] - A subclass of [[ActorOf]] that only ever provides a single instance
 *                           of the [[ActorRef]]. This can never also be a [[CustomActorOf]]
 *
 * Usage:
 *
 * {{{
 *   class MyActor(otherActor: ActorOf[OtherActor]) {
 *
 *     lazy val other: ActorRef = otherActor.create()
 *   }
 *
 *   class MyCustomActor(key: String, otherActor: CustomActorOf[String, OtherActor]) {
 *
 *     lazy val other: ActorRef = otherActor.create(key)
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
 * 3. Multiple actors can share the same [[Constructor]] or [[DefaultConstructor]] for an actor.
 *
 * 4. Manual calls that require an [[ActorOf]] can always be swapped for a [[SingletonActorOf]].
 *
 * 5. Compiler errors instead of assuming any particular construction pattern.
 */
object ActorOf {

  /**
   * Builds the [[ActorOf]] using the given a block of code that constructs a new instance.
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
  def apply[A <: Actor: ClassTag](init: => A): ConfigurableActorOf[A] =
    new ActorOf[A](() => init, identity) with ConfigurableActorOf[A]

  /**
   * Builds the [[ActorOf]] using the given [[DefaultConstructor]].
   *
   * @param constructor the default constructor to use to build the Actor.
   */
  def apply[A <: Actor](constructor: DefaultConstructor[A])(implicit clsTag: ClassTag[A]) =
    new ActorOf[A](constructor.construct, identity) with ConfigurableActorOf[A]

  /**
   * Builds the [[CustomActorOf]] using a given factory function and arguments to use by default.
   *
   * @param constructor the function used to construct new instances of the Actor.
   * @param defaultArgs the default arguments to use when the caller doesn't provide them.
   */
  def apply[A <: Actor: ClassTag, P](constructor: P => A, defaultArgs: => P): ConfigurableCustomActorOf[A, P] =
    new CustomActorOf[A, P](Constructor(constructor), () => defaultArgs, identity) with ConfigurableCustomActorOf[A, P]

  /**
   * Builds a [[CustomActorOf]] using the given [[Constructor]].
   *
   * @note You must call `.withDefaultArgs` on the return value in order to actually get the [[CustomActorOf]].
   *       This just enables a nicer syntax for building the [[CustomActorOf]]
   *
   * @param constructor the function used to construct new instances of the Actor.
   *
   * @return a [[CustomActorOfBuilder]] to enable configuring Props and providing the default arguments.
   */
  def apply[A <: Actor, P](constructor: Constructor[P, A])(implicit clsTag: ClassTag[A]): CustomActorOfBuilder[A, P] =
    new CustomActorOfBuilder[A, P](constructor, identity)

  /**
   * An immutable builder that holds on to all the parameters before building a [[CustomActorOf]]
   */
  final class CustomActorOfBuilder[A <: Actor: ClassTag, P] private[ActorOf] (
    constructor: Constructor[P, A],
    config: Props => Props) {

    /**
     * Defines a mechanism to customize for the [[CustomActorOf]]'s props config function.
     *
     * @param replacement replaces the current props config function
     * @return a new [[CustomActorOfBuilder]] to allow adding the required default arguments
     */
    def withPropsConfig(replacement: Props => Props): CustomActorOfBuilder[A, P] =
      new CustomActorOfBuilder[A, P](constructor, replacement)

    /**
     * Provides the default arguments to build the specific type of Actor.
     *
     * @note this is required to have a fully initialized [[CustomActorOf]]
     *
     * @param args a block of code to get the default arguments used to construct the Actor
     * @return a regular [[CustomActorOf]] to avoid leaking the configuration method in the result
     */
    def withDefaultArgs(args: => P): CustomActorOf[A, P] =
      new CustomActorOf[A, P](constructor, () => args, identity)
  }

  /**
   * Create a singleton instance of the given [[ActorOf]].
   */
  implicit def singleton[A <: Actor](of: ActorOf[A]): SingletonActorOf[A] = of.singleton

}

/**
 * A common base class for [[ActorOf]], [[SingletonActorOf]], and [[CustomActorOf]].
 *
 * @tparam A the specific Actor class
 */
class ActorOf[A <: Actor: ClassTag] private[akka] (
  protected[akka] val initialize: () => A,
  protected[akka] val configure: Props => Props) {

  /**
   * The [[ClassTag]] of the Actor for building props.
   */
  protected[akka] lazy val actorClassTag: ClassTag[A] = implicitly

  /**
   * Creates the actor instance as a child of the current context.
   */
  protected def actorOf(initialize: => A, name: String = null)(implicit context: ActorRefFactory): ActorRef = {
    name match {
      case null => context.actorOf(configure(Props(initialize)(actorClassTag)))
      case _    => context.actorOf(configure(Props(initialize)(actorClassTag)), name)
    }
  }

  /**
   * Construct an instance of the actor OR get the singleton actor.
   *
   * @param context the context where this actor is being created
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def ref(implicit context: ActorRefFactory): ActorRef = actorOf(initialize())

  /**
   * Construct an instance of the actor
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def ref(name: String)(implicit context: ActorRefFactory): ActorRef = actorOf(initialize(), name)

  /**
   * Create the instance of the actor using the provided initializer value.
   *
   * @param init the block of code that will initialize the actor
   * @param context the context where this actor is being created
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def constructRefUsing(init: => A)(implicit context: ActorRefFactory): ActorRef = actorOf(init)

  /**
   * Create the instance of the actor using the provided initializer value and the given name.
   *
   * @param init the block of code that will initialize the actor
   * @param name the name to give the actor, acts as the relative path
   * @param context the context where this actor is being created
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def constructRefUsing(init: => A, name: String)(implicit context: ActorRefFactory): ActorRef = actorOf(init, name)

  /**
   * Create a singleton version of this [[ActorOf]].
   *
   * @note if you need an instance of this in the dependent class, you should be able to require it in
   *       the constructor.
   */
  def singleton: SingletonActorOf[A] = new ActorOf[A](initialize, configure) with SingletonActorOf[A]
}

/**
 * A configurable [[ActorOf]] that allows you to update the Props configuration.
 *
 * This is separate from [[ActorOf]] to avoid classes needing to know how the props are configured at the module
 * level, unless they actually need to.
 *
 * @note this is primarily to allow configuration at the module level without also implicitly exposing the
 *       capabilities to dependant Actor classes.
 */
trait ConfigurableActorOf[A <: Actor] extends ActorOf[A] {

  /**
   * Defines a mechanism to customize for the [[ActorOf]]'s props config function.
   *
   * @param replacement replaces the current props config function
   * @return a regular [[ActorOf]] to avoid leaking the configuration method in the result
   */
  def withPropsConfig(replacement: Props => Props): ActorOf[A] =
    new ActorOf[A](this.initialize, replacement)(actorClassTag)
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
  override protected def actorOf(initialize: => A, name: String = null)(implicit context: ActorRefFactory): ActorRef = {
    if (actor ne null) {
      if (name eq null) actor
      else throw new SingletonActorAlreadyInitialized(actor)
    }
    else synchronized {
      if (actor eq null) {
        actor = super.actorOf(initialize, name)
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

/**
 * Defines the mechanisms for constructing and instance of a specific subclass of [[Actor]] using the provided
 * argument type.
 *
 * @note this cannot be made into a [[SingletonActorOf]].
 *
 * @tparam P the type of parameters required to build an instance of the specific Actor class
 * @tparam A the specific Actor class
 */
class CustomActorOf[A <: Actor: ClassTag, P] private[akka] (
  protected[akka] val defaultConstructor: Constructor[P, A],
  protected[akka] val defaultArgs: () => P,
  configure: Props => Props
) extends ActorOf[A](() => defaultConstructor(defaultArgs()), configure) {

  /**
   * Use the given args and the implicitly provided constructor to create a new [[ActorRef]].
   *
   * @note to call this with the default argument, use [[ref]] instead, as it is shared
   *       with the common [[ActorOf]] and is less to type.
   *
   * @param args the argument to pass to the default constructor
   * @param context the context to use when creating the actor
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def constructRef(args: => P, name: String = null)(implicit context: ActorRefFactory): ActorRef = {
    actorOf(defaultConstructor(args), name)
  }

  /**
   * Use an implicit [[Constructor]] of the appropriate type to create this instance.
   *
   * @param args the argument to pass to the constructor function
   * @param context the context to use when creating the actor
   * @param constructor the implicit constructor function
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def constructRefImplicitly(args: => P = defaultArgs(), name: String = null)
    (implicit context: ActorRefFactory, constructor: Constructor[P, A]): ActorRef = {
    actorOf(constructor(args), name)
  }

  /**
   * Use the given constructor function with the default arguments to create a new [[ActorRef]].
   *
   * @param constructor the function to use to construct an instance of the actor
   * @param args the argument to pass to the constructor function
   * @param context the context to use when creating the actor
   */
  @throws[ConfigurationException]("if Akka is not configured properly")
  @throws[InvalidActorNameException]("if the actor name is already taken")
  @throws[SingletonActorAlreadyInitialized]("if this is called twice on a SingletonActorOf")
  def constructRefUsing(constructor: P => A, args: => P = defaultArgs(), name: String = null)
    (implicit context: ActorRefFactory): ActorRef = {
    actorOf(constructor(defaultArgs()), name)
  }

  /**
   * Create a singleton version of this [[ActorOf]].
   *
   * @note if you need an instance of this in the dependent class, you should be able to require it in
   *       the constructor.
   */
  override def singleton: CustomActorOf[A, P] with SingletonActorOf[A] =
    new CustomActorOf[A, P](defaultConstructor, defaultArgs, configure) with SingletonActorOf[A]
}

/**
 * A configurable [[CustomActorOf]] that allows you to update the Props configuration.
 *
 * This is separate from [[CustomActorOf]] to avoid classes needing to know how the props are configured at the module
 * level, unless they actually need to.
 *
 * @note this is primarily to allow configuration at the module level without also implicitly exposing the
 *       capabilities to dependant Actor classes.
 */
trait ConfigurableCustomActorOf[A <: Actor, P] extends CustomActorOf[A, P] {

  /**
   * Defines a mechanism to customize for the [[CustomActorOf]]'s props config function.
   *
   * @param replacement replaces the current props config function
   * @return a regular [[CustomActorOf]] to avoid leaking the configuration method in the result
   */
  def withPropsConfig(replacement: Props => Props): CustomActorOf[A, P] =
    new CustomActorOf[A, P](this.defaultConstructor, this.defaultArgs, replacement)(actorClassTag)
}
