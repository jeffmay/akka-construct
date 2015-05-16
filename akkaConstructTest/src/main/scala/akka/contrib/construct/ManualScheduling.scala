package akka.contrib.construct

trait ManualScheduling extends ImplicitScheduler {

  override implicit protected lazy val scheduler: ManualScheduler = new ManualScheduler
}