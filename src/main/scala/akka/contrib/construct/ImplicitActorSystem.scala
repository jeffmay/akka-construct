package akka.contrib.construct

import akka.actor.ActorSystem

/**
 * Provides an implicit [[ActorSystem]] for accessing configs or creating actors.
 *
 * @note there is typically only one [[ActorSystem]] per JVM.
 *
 * This trait is a foundation for compositional inheritance with the mixin pattern.
 */
trait ImplicitActorSystem {

  implicit protected def actorSystem: ActorSystem
}
