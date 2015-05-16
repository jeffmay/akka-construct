package akka.contrib.construct

import akka.actor._
import akka.testkit.TestKitBase

/**
 * Provides the [[TestKitBase]] with an [[ActorSystem]] named using the test class.
 *
 * It also provides this actor system in a way that is compatible with [[ImplicitActorSystem]] mixins.
 *
 * This is a good default for Akka [[TestKitBase.system]]s, but consider extending [[AkkaTestKit]] for extra goodies.
 */
trait DefaultTestKitActorSystem extends ImplicitActorSystem {
  // Using a self-type to make the dependence on the underlying Akka TestKit more explicit
  this: TestKitBase =>

  override lazy val system: ActorSystem = ActorSystem(getClass.getSimpleName)

  @inline final override implicit protected def actorSystem: ActorSystem = system
}

/**
 * Provides an [[ActorSystem]] without requiring that you extend [[TestKitBase]].
 * 
 * This is a good default for tests that don't actually use the [[ActorSystem]], but don't want to mock it out.
 */
trait DefaultTestActorSystem extends ImplicitActorSystem {

  override implicit protected val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
}
