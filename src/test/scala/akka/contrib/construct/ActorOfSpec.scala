package akka.contrib.construct

import akka.actor.{ActorRef, Actor}
import akka.routing.RouterConfig
import ActorOfSpec._
import org.scalatest.FlatSpec
import org.scalatest.mock.MockitoSugar

class ActorOfSpec extends FlatSpec with AkkaTestKit with SpecNumber with MockitoSugar {

  def anActorOf(
    refType: String,
    convertToRef: ActorOf[Child] => ActorRef,
    convertToNamedRef: (ActorOf[Child], String) => ActorRef,
    convertToCustomRef: (ActorOf[Child], => Child) => ActorRef): Unit = {

    it should s"create the $refType" in {
      val childActor = ActorOf(new Child(Seq()))
      val child = convertToRef(childActor)
      val msg = "ohhai"
      child ! msg
      expectMsg(msg)
    }

    it should s"create the $refType with the supplied name for the ActorPath.name" in {
      val expected = s"child-$n"
      val childActor = ActorOf(new Child(Seq()))
      val child = convertToNamedRef(childActor, expected)
      assert(child.path.name == expected)
    }

    it should s"create the $refType using the provided constructor" in {
      val expected = Seq("correct")
      val childActor = ActorOf(new Child(Seq("wrong")))
      val child = convertToCustomRef(childActor, new Child(expected))
      child ! GetChildPath
      expectMsg(expected)
    }
  }

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

  behavior of "ActorOf"
  
  it should behave like anActorOf("TestActorRef", _.ref, _.ref(_), _.constructRefUsing(_))

  def aCustomActorOf(
    refType: String,
    convertChildToRef: ActorOf[Child] => ActorRef,
    convertChildToNamedRef: (ActorOf[Child], String) => ActorRef,
    convertParentToRef: ActorOf[CustomChildMaker] => ActorRef
    ): Unit = {

    it should s"create the $refType with the supplied name for the ActorPath.name" in {
      val expected = s"child-$n"
      val childActor = ActorOf(new Child(Seq()))
      val child = convertChildToNamedRef(childActor, expected)
      assert(child.path.name == expected)
    }

    it should s"create the $refType with the default args using the implicit constructor" in {
      val prefix = List("prefix")
      val constructor = Constructor((path: Seq[String]) => new Child(prefix ++ path))
      val expectedArgs = Seq("correct", "args")
      val expectedResult = prefix ++ expectedArgs
      val childActor = ActorOf[Child, Seq[String]](constructor).withDefaultArgs(expectedArgs)
      val child = convertChildToRef(childActor)
      child ! GetChildPath
      expectMsg(expectedResult)
    }

    it should s"create the child $refType with the default args" in {
      val expected = Seq("correct", "args")
      val childActor = ActorOf[Child, Seq[String]](Child.constructor).withDefaultArgs(expected)
      val childMaker = ActorOf {
        new CustomChildMaker(Seq("parent"), childActor, fail("should not access child args"), useDefault = true)
      }
      val parent = convertParentToRef(childMaker)
      parent ! GetChildPath
      expectMsg(expected)
    }

    it should s"create the child $refType with the supplied constructor" in {
      val expected = Seq("correct", "args")
      val childActor = ActorOf[Child, Seq[String]](Child.constructor).withDefaultArgs(Seq("wrong", "args"))
      val childMaker = ActorOf(new CustomChildMaker(Seq("a"), childActor, expected, useDefault = false))
      val parent = convertParentToRef(childMaker)
      parent ! GetChildPath
      expectMsg(expected)
    }
  }

  /**
   * As provided by [[ImplicitTestActorOf]] in [[AkkaTestKit]]
   */
  behavior of "TestCustomActorOf"

  it should behave like aCustomActorOf("TestActorRef", _.testRef, _.testRef(_), _.testRef)

  it should "create an ActorRef with the props supplied by the props config function" in {
    val expected = mock[RouterConfig]("Fake Router provided by Props")
    val childActor = ActorOf(new Child(_: Seq[String]), Seq())
    // assuming that TestActorRef does not muck with RouterConfig
    val childActorWithProps = childActor.withPropsConfig(_.withRouter(expected))
    val child = childActorWithProps.testRef
    assert(child.props.routerConfig == expected)
  }

  behavior of "CustomActorOf"

  it should behave like aCustomActorOf("an ActorRef", _.ref, _.ref(_), _.ref)

}

object ActorOfSpec extends ImplicitTestActorOf {

  case object GetPath

  case object GetChildPath

  class CustomChildMaker(
    val value: Seq[String],
    val childActor: CustomActorOf[Child, Seq[String]],
    childActorArgs: => Seq[String],
    useDefault: Boolean) extends Actor {

    val child =
      if (useDefault) childActor.ref
      else childActor.constructRef(childActorArgs)

    override def receive: Actor.Receive = {
      case GetPath => sender() ! value
      case GetChildPath =>
        val forward = sender()
        child.tell(GetChildPath, forward)
    }
  }

  class Child(val value: Seq[String]) extends Actor {

    var received: Seq[Any] = Seq()

    override def receive: Actor.Receive = {
      case GetChildPath =>
        sender() ! value
      case x =>
        received :+= x
        sender() ! x
    }
  }

  object Child {
    val constructor: Constructor[Seq[String], Child] = Constructor(new Child(_))
  }
}