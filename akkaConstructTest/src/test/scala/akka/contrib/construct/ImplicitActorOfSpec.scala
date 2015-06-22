package akka.contrib.construct

import akka.actor.Actor
import akka.contrib.construct.ActorOfSpec._
import akka.routing.RouterConfig

class ImplicitActorOfSpec extends ActorOfSpec with ImplicitTestActorOf {

  /**
   * as provided by [[ImplicitTestActorOf]] in [[AkkaTestKit]]
   */
  behavior of "TestActorOfOps"

  it should behave like anActorOf("ActorRef", _.testRef, _.testRef(_), _.constructTestRefUsing(_))

  it should "create the TestActorRef with the props supplied by the props config function" in {
    val expected = mock[RouterConfig]("Fake Router provided by Props")
    val childActor = ActorOf(new Child(Seq()))
    // assuming that TestActorRef does not muck with RouterConfig
    val childActorWithProps = childActor.withPropsConfig(_.withRouter(expected))
    val child = childActorWithProps.testRef
    assert(child.props.routerConfig == expected)
  }

  /**
   * As provided by [[ImplicitTestActorOf]] in [[AkkaTestKit]]
   */
  behavior of "TestCustomActorOf"

  it should behave like aCustomActorOf("TestActorRef", _.testRef, _.testRef(_), _.testRef)

  it should "create an ActorRef with the props supplied by the props config function" in {
    val expected = mock[RouterConfig]("Fake Router provided by Props")
    val childActor = Child.Construct.actorOf(Seq())
    // assuming that TestActorRef does not muck with RouterConfig
    val childActorWithProps = childActor.withPropsConfig(_.withRouter(expected))
    val child = childActorWithProps.testRef
    assert(child.props.routerConfig == expected)
  }

}

class TestActorRefOpsInsideActorSpec extends Actor with ImplicitTestActorOf {
  val selfActor = ActorOf(new TestActorRefOpsInsideActorSpec)
  override def receive: Receive = {
    case "test" =>
      // this should compile
      sender() ! selfActor.testRef
  }
}
