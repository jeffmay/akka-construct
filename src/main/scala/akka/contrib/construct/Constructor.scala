package akka.contrib.construct

import scala.language.implicitConversions

/**
 * Represents a function that constructs an instance of a specific type from an argument.
 *
 * This is used as a handle for implicit parameters to provide a way to initialize an instance
 * of a class that does not have a companion object with an apply function that does this, or
 * to make this apply method available implicitly.
 *
 * @tparam P the parameter required to create an instance of R
 * @tparam R the type of instance constructed
 */
sealed trait Constructor[-P, +R] {

  /**
   * A method for syntactic convenience.
   *
   * @see [[construct()]]
   *
   * @note this does not override function because it would cause issues with overloaded
   *       methods and implicit look-ups.
   */
  final def apply(args: P): R = construct(args)

  /**
   * Constructs a new instance from the given args.
   */
  def construct(args: P): R
}

object Constructor {

  /**
   * An alias to [[DefaultConstructor.apply]] for simplicity.
   */
  def apply[R](constructor: => R): DefaultConstructor[R] = DefaultConstructor(constructor)

  /**
   * Builds a [[Constructor]] from the given function.
   *
   * Example:
   * {{{
   *   class Instance(s: String, i: Int)
   *
   *   object Instance {
   *     // the default constructor function used when summoning a Constructor[(String, Int), Instance]
   *     implicit val constructor = Constructor((new Instance(_, _)).tupled)
   *   }
   * }}}
   *
   * @note this alias is to allow being agnostic of the type of constructor without conflating
   *       the inheritance hierarchy of [[Constructor]]
   *
   * @param constructor a call by name block of code that initializes this instance.
   */
  def apply[P, R](constructor: P => R): Constructor[P, R] = new Constructor[P, R] {
    override def construct(args: P): R = constructor(args)
  }

  /**
   * Build a constructor in a piecemeal fashion.
   *
   * This allows for better type inference for when you know the parameter type.
   * {{{
   *   class Instance(s: String)
   *
   *   Constructor.from[String].build(new Instance(_))  // Constructor[String, Instance]
   * }}}
   *
   * @tparam P the type of arguments used to construct this instance
   * @return A builder for chaining the function from the argument
   */
  def from[P]: ConstructorBuilder[P] = new ConstructorBuilder[P] {}

  /**
   * A handy immutable builder for ease of building a constructor function using chaining.
   */
  sealed trait ConstructorBuilder[P] {

    /**
     * Provide a constructor function to use for this constructor.
     *
     * @param constructor a function used to initialize an instance for this constructor.
     * @return a constructor function for implicit or explicit use.
     */
    def build[R](constructor: P => R): Constructor[P, R] = new Constructor[P, R] {
      override def construct(args: P): R = constructor(args)
    }
  }
}

/**
 * Represents a function that constructs an instance of a specific type without any arguments.
 *
 * This is used as a handle for implicit parameters to provide a way to initialize an instance
 * of a class that does not have a companion object with an apply function that does this, or
 * to make this apply method available implicitly.
 *
 * @note This does not extend from Constructor[Unit, R] to avoid ambiguous implicits.
 *       Furthermore, it is not necessarily the case that every class with more than zero arguments
 *       have a Constructor of that argument type tuple. You could define a [[DefaultConstructor]]
 *       instead, if you don't want the constructor to be parameterizable.
 *
 * @tparam R the type of instance constructed
 */
sealed trait DefaultConstructor[+R] {
  default =>

  /**
   * Convert this into a [[Constructor]], in case you need to.
   * 
   * Typically you should not need this, but if you want to have a generic method on [[Constructor]],
   * this will enable you to either explicitly or implicitly convert a [[DefaultConstructor]] into
   * a [[Constructor]].
   */
  def toUnitConstructor: Constructor[Unit, R] = new Constructor[Unit, R] {
    override def construct(args: Unit): R = default.construct()
  }

  /**
   * Construct a new instance.
   */
  def construct(): R
}

object DefaultConstructor {

  /**
   * Builds a [[DefaultConstructor]] from the initialization block.
   *
   * Example:
   * {{{
   *   class Instance(s: String, i: Int)
   *
   *   object Instance {
   *     // the default constructor to use when summoning a DefaultConstructor[Instance]
   *     implicit val defaultConstructor = DefaultConstructor(new Instance("arg1", 2))
   *   }
   * }}}
   *
   * @note this alias is to allow being agnostic of the type of constructor without conflating
   *       the inheritance hierarchy of [[Constructor]]
   *
   * @param constructor a call by name block of code that initializes this instance.
   */
  def apply[R](constructor: => R): DefaultConstructor[R] = new DefaultConstructor[R] {
    override def construct(): R = constructor
  }

  /**
   * Convert a [[DefaultConstructor]] into a [[Constructor]] to aid with generic methods.
   *
   * @note Scala will not apply two levels of implicit conversion
   *       nor will it apply an implicit conversion to get an implicit parameter
   *       (unless the implicit conversion takes the argument implicitly)
   *
   *       You'll notice the following implicit conversion does not take the constructor
   *       implicitly. This is on purpose. It is to avoid ambiguous implicits when requiring
   *       a [[DefaultConstructor]].
   */
  implicit def toUnitConstructor[R](constructor: DefaultConstructor[R]): Constructor[Unit, R] =
    constructor.toUnitConstructor
}