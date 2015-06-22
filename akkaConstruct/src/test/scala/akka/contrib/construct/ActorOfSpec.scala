package akka.contrib.construct

import akka.actor._
import akka.contrib.construct.ActorOfSpec._
import akka.testkit.{ImplicitSender, TestKitBase}
import org.scalatest.FlatSpec
import org.scalatest.mock.MockitoSugar

class ActorOfSpec extends FlatSpec
with TestKitBase
with ImplicitSender
with ImplicitActorSystem
with SpecNumber
with MockitoSugar {
  override lazy val system: ActorSystem = ActorSystem(getClass.getSimpleName)
  override implicit protected def actorSystem: ActorSystem = system

  "ActorOf" should behave like anActorOf("TestActorRef", _.ref, _.ref(_),
    (actorOf, child) => actorOf.withPropsConfig(_ => Props(child)).ref
  )

  "CustomActorOf" should behave like aCustomActorOf("ActorRef", _.ref, _.ref(_), _.ref)

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
      object Other extends Child.Construct {
        override def actorOf(path: Seq[String])(implicit params: ConstructActorContext[Child]): ActorOf[Child] = {
          super.actorOf(prefix ++ path)
        }
      }
      val path = Seq("correct", "args")
      val expectedResult = prefix ++ path
      val childActor = Other.actorOf(path)
      val child = convertChildToRef(childActor)
      child ! GetChildPath
      expectMsg(expectedResult)
    }

    it should s"create the child $refType with the default args" in {
      val expected = Seq("correct", "args")
      val constructParent = new CustomChildMaker.Construct(expected, useDefault = true)
      val parent = convertParentToRef(constructParent.actorOf(Seq("wrong")))
      parent ! GetChildPath
      expectMsg(expected)
    }

    it should s"create the child $refType with the supplied constructor" in {
      val expected = Seq("correct", "args")
      val constructParent = new CustomChildMaker.Construct(Seq("wrong"), useDefault = false)
      val childMaker = constructParent.actorOf(expected)
      val parent = convertParentToRef(childMaker)
      parent ! GetChildPath
      expectMsg(expected)
    }
  }
}

object ActorOfSpec {

  case object GetPath

  case object GetChildPath

  class CustomChildMaker(val value: Seq[String], childConstruct: Child.Construct) extends Actor {

    lazy val child = childConstruct.actorOf(value).ref("hello")

    override def receive: Actor.Receive = {
      case GetPath => sender() ! value
      case GetChildPath =>
        val forward = sender()
        child.tell(GetChildPath, forward)
    }
  }
  
  object CustomChildMaker {
    class Construct(defaultArgs: Seq[String], useDefault: Boolean) extends ConstructActor.P1 with ConstructActor {
      override type ActorClass = CustomChildMaker
      override type P1 = Seq[String]
      override def actorOf(path: Seq[String])
        (implicit params: ConstructActorContext[CustomChildMaker]): ActorOf[CustomChildMaker] = {
        // Intercept construction logic
        if (useDefault) ActorOf(new CustomChildMaker(defaultArgs, Child.Construct))
        else ActorOf(new CustomChildMaker(path, Child.Construct))
      }
      override def actor(implicit params: ConstructActorContext[CustomChildMaker]): ActorOf[CustomChildMaker] = {
        actorOf(defaultArgs)
      }
    }
    object Construct extends Construct(Seq("default"), useDefault = true)
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
    class Construct extends ConstructActor.P1 with ConstructActor {
      override type ActorClass = Child
      override type P1 = Seq[String]
      override def actorOf(path: Seq[String])(implicit params: ConstructActorContext[Child]): ActorOf[Child] = {
        ActorOf(new Child(path))
      }
      override def actor(implicit params: ConstructActorContext[Child]): ActorOf[Child] = actorOf(Seq())
    }
    object Construct extends Construct
  }
}



