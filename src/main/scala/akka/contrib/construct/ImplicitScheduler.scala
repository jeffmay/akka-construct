package akka.contrib.construct

import akka.actor.{ActorSystem, Actor, Scheduler}

/**
 * Provides an implicit [[Scheduler]] for scheduling actions to happen in the future.
 */
trait ImplicitScheduler {

  implicit protected def scheduler: Scheduler
}

/**
 * Provides the scheduler of the mixed in [[ImplicitActorSystem]].
 */
trait ActorSystemScheduler extends ImplicitScheduler {
  self: ImplicitActorSystem =>

  override implicit protected def scheduler: Scheduler = actorSystem.scheduler
}

/**
 * Extend this mixin to allow test code to replace the standard [[ActorSystem.scheduler]] with
 * another [[ImplicitScheduler]].
 *
 * This provides the subclassing Actor's [[Actor.context.system.scheduler]], but in such a way
 * that it can be overriden with other [[ImplicitScheduler]]s.
 *
 * @note while this is more extensible than using [[Actor.context.system.scheduler]], it is
 *       still more rigid than requiring the [[Scheduler]] in the constructor.
 *
 *       This is useful for building a common [[Actor]] trait that requires a [[Scheduler]],
 *       when you want to avoid requiring class constructor boilerplate.
 *
 *       For stand-alone classes, it is probably best to require the [[Scheduler]] in the
 *       constructor to make their usage more explicit.
 */
trait ActorScheduler extends ActorSystemScheduler with ImplicitActorSystem {
  this: Actor =>

  @inline final override implicit protected def actorSystem: ActorSystem = this.context.system
}