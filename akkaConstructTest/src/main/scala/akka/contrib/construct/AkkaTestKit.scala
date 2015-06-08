package akka.contrib.construct

import akka.testkit.{ImplicitSender, TestKitBase}

/**
 * Provides Akka's [[TestKitBase]] with a default [[akka.actor.ActorSystem]] and with the ability to
 * mixin other implementations of [[akka.actor.Scheduler]]s and with helpful implicits.
 */
trait AkkaTestKit
  extends TestKitBase
  with DefaultTestKitActorSystem
  with ActorSystemScheduler
  with ImplicitSender
  with ImplicitTestActorOf
