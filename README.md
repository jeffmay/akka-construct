# TESTING

<a href='https://travis-ci.org/jeffmay/akka-construct'>
  <img src='https://travis-ci.org/jeffmay/akka-construct.svg' alt='Build Status' />
</a>
<a href='https://coveralls.io/github/jeffmay/akka-construct?branch=master'>
  <img src='https://coveralls.io/repos/jeffmay/akka-construct/badge.svg?branch=master&service=github' alt='Coverage Status' />
</a>
<table>
  <tr>
    <th>akka-construct</th>
    <th>akka-construct-test</th>
  </tr>
  <tr>
    <td>
      <a href='https://bintray.com/jeffmay/maven/akka-construct/_latestVersion'>
        <img src='https://api.bintray.com/packages/jeffmay/maven/akka-construct/images/download.svg'>
      </a>
    </td>
    <td>
      <a href='https://bintray.com/jeffmay/maven/akka-construct-test/_latestVersion'>
        <img src='https://api.bintray.com/packages/jeffmay/maven/akka-construct-test/images/download.svg'>
      </a>
    </td>
  </tr>
</table>

# akka-construct

Allows for type-safe Akka actor constructor injection using a `Construct` object.

# Problem Statement

If you are like me, you probably love dependency injection for testability, dependency management, avoiding scope creep, etc.
If you are like me, you probably also love Akka and maybe even compile-time dependency injection (DI). Unfortunately, there
are some missing pieces for this in Akka. For the most part, you don't need anything special for DI because Actors can be
constructed the same way as any other class. The problem arises when you want to take an `ActorRef` as an argument in your
actor class constructor.

```scala
class SoccerActor(coach: ActorRef, referee: ActorRef) extends Actor {
  import SoccerActor._

  def recieve = {
    case RedFlag => referee ! Say("C'mon!")
    case Timeout => coach ! Say("I got this coach!")
  }
}
object SoccerActor {
  case object RedFlag
  case object Timeout
  case class Say(message: String)
}
```

## Specifying the type of Actor

The problem is that most DI frameworks bind your constructor arguments by their type. Since `ActorRef` is a shared type that
obscures the construction logic and the underlying location of the actor (remote vs local). One way around this is to use a
simple typed wrapper trait:

```scala
class SoccerActor(coachActor: ActorOf[CoachActor], refereeActor: ActorOf[RefereeActor]) extends Actor {
  import SoccerActor._

  val coach = coachActor.ref
  val referee = refereeActor.ref

  def recieve = {
    case RedFlag => referee ! Say("C'mon!")
    case Timeout => coach ! Say("I got this coach!")
  }
}
object SoccerActor {
  case object RedFlag
  case object Timeout
  case class Say(val message: String)
}
```

Calling `.ref` will produce an instance of `ActorRef`. To always dispense the same actor ref you can call `.singleton` on
the `ActorOf` to get a factory that always produces the same actor ref instance. Using this library, `ActorOf` will also
provide the ability for the constructed actor to access the `ActorContext` of the constructing Actor during construction.

## Constructing Actors with Parameters

You might run into a situation where you need to construct an actor based on other values known only at runtime.
For this scenario, you could inject a function `Args => ActorOf[T]`, however, this function type must be repeated in every
constructor that wants to construct this type of `Actor`. You could use a type alias to this function, but there are some
downsides to this approach.

1. Alternate constructors require separate function type aliases
2. Function arguments don't have a standard way to document them
3. Taking advantage of the implicit actor context in the constructor function requires boilerplate

The approach suggested by this library is to use a `Construct` class with abstract type members. To continue our example,
let's define the `CoachActor` with the ability to construct new `SoccerActor`s:

```scala
class CoachActor(
  players: Set[PlayerInfo],
  constructSoccerPlayer: SoccerActor.Construct,
  constructReferee: RefereeActor.Construct) {

  // Retrieve a reference to the referee actor using a no-argument constructor
  val referee = constructReferee.actor.ref

  // We will now construct a player ActorRef for every player we know about:
  var playerActors: Map[String, ActorRef] = players.map(p => p.id -> constructSoccerPlayer.actorOf(p).ref)

  // Setup the known state about these players
  var playerStates: Map[String, SoccerPlayer.State] = players.map(p => p.id -> SoccorPlayer.State.unknown)

  // Some fun example code involving player state.
  // None of the following code is specific to this library.
  def recieve = {
    case RedFlag => referee ! Say("What was that!?")
    case SoccorActor.Injury(playerRef, player, injury) =>
      playerRef ! Say(s"Ok ${player.name}, get some rest on that ${injury}.")
      val injuredPlayerState = playerStates(player.id)
      playerStates = player.id -> injuredPlayerState.copy(
        health = SoccerPlayer.Health.Injured,
        position = SoccerPlayer.Position.Bench
      )
      def maybeSuitablePlayer = playerStates collectFirst {
        case (player, state) if state.health > SoccerPlayer.Health.Okay => player
      } flatMap { player =>
        playerActors.get(player.id)
      }
      maybeSuitablePlayer match {
        case Some(replacement) =>
          val replacementPlayerState = playerStates(replacement.id)
          playerStates += replacement.id -> replacementPlayerState.copy(position = SoccerPlayer.Position.Field)
          replacement ! Say(s"Alright, ${replacement.name}, you're up!")
        case None =>
          referee ! Forfeit
      }
  }
}
```

In this example, we show the use of the `constructSoccerPlayer.actorOf(???).ref` method for creating an Actor using a
parameterized constructor as well as the default constructor of `constructReferee.actor.ref`. In both cases, the constructor
has access to the actor context and can use it to decide what `Props` to use to constructor the `ActorRef`.

# Creating Actor Constructors

The basic idea is that every `Actor` class will have a companion object. On this companion object, you would define a `Construct` class and pick a `ConstructActor.P#` where `#` is the number of parameters (1 through 10) you want to define for
constructing this actor. You can combine multiple traits together to support multiple constructor methods or use
`ConstructActor` to support a parameterless constructor.

```scala
object SoccerActor {
  // Creates an ConstructActor with a 1-argument constructor
  class Construct extends ConstructActor.P1 {
    override type ActorClass = SoccerActor
    override type P1 = PlayerInfo
    // This method has access to the playerInfo as well as implicit actor context information
    override def actorOf(playerInfo: PlayerInfo)(
      implicit params: ConstructActorContext[SoccerActor]): ActorOf[SoccerActor] = {
      ActorOf(new SoccerActor(playerInfo))
    }
  }
  object Construct extends Construct
  ...
}
```

We also define an `object Construct` to make it easy to provide this constructor, however, you are free to construct this
factory using dependency injection as well.

Notice that none of this code is really spectacular. This library provides more of a convention than library support code.
This library primarily provides the types needed for implicit syntax and, if you use the `akka-construct-test` package,
you have an easy way to build typed `TestActorRef`s.

# Testing your Actors

So now that we have the benefits of dependency injection for all of our arguments in Akka, we can use `akka-construct-test`
module to add support for `TestActorRef[_]`s. To support constructing these actors, just add:

```scala
import akka.contrib.construct.ImplicitTestActorOf._

class TestActor extends Actor {
  var state = 0
  def receive = ???
}
object TestActor {
  class Construct extends ConstructActor {
    override def actor(implicit params: ConstructActorContext[ExampleActor]): ActorOf[ExampleActor] = {
      new TestActor
    }
  }
  object Construct extends Construct
}

class MyTest extends WordSpec {
  "ImplicitTestActorOf should support constructing typed TestActorRefs" in {
    val exampleRef = TestActor.Construct.actor.testRef
    val exampleActor = exampleRef.underlyingActor
    assertResult(0)(exampleActor.state)
  }
}
```

