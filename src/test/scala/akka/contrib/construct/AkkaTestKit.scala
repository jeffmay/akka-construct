package akka.contrib.construct

import akka.testkit.{ImplicitSender, TestKitBase}

trait AkkaTestKit
  extends TestKitBase
  with DefaultTestKitActorSystem
  with ActorSystemScheduler
  with ImplicitSender
  with ImplicitTestActorOf
