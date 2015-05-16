package akka.contrib.construct

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.FutureTimeoutSupport

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Provides helper methods for [[Future]]s using Akka.
 *
 * This gives you [[FutureTimeoutSupport]] as well as the ability to timeout a future after a given
 * period of time using the [[Scheduler]].
 *
 * {{{
 *   Future {
 *     // future work
 *   } withTimeout 5.seconds
 * }}}
 */
trait AkkaFutureHelpers extends FutureTimeoutSupport {
  outer =>

  /**
   * Short-circuits the future after the provided duration by completing the future with [[Failure]].
   *
   * @note this does not cancel the Future. Side-effects can still happen after the Future has timed out.
   *
   * @param limit a finite amount of time to wait for the future to finish before failing the promise
   * @param using the Scheduler to use to timeout the request
   * @param future the Future to execute after the timer has started
   * @param ec the context in which to complete the future with failure
   * @tparam T the type of result in the Future
   */
  def withTimeout[T](limit: FiniteDuration, using: Scheduler)(future: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val promise = Promise[T]()
    // schedule the timeout
    using.scheduleOnce(limit) {
      // fail the promise after the timeout (if it hasn't already succeeded)
      promise tryFailure new java.util.concurrent.TimeoutException(s"Future timed out after $limit.")
    }
    // complete the new future with the original future
    promise completeWith future
    promise.future
  }

  implicit class FutureWithTimeout[T](future: Future[T]) {

    def withTimeout(limit: FiniteDuration, using: Scheduler)(implicit ec: ExecutionContext): Future[T] = {
      outer.withTimeout(limit, using)(future)
    }

    def withTimeout(limit: FiniteDuration)(implicit system: ActorSystem, ec: ExecutionContext): Future[T] = {
      outer.withTimeout(limit, system.scheduler)(future)
    }

    /**
     * Lifts the value or exception in the Future into a Try result.
     * 
     * @param ec the context to execute the recovery in
     */
    def lift(implicit ec: ExecutionContext): Future[Try[T]] = future.map(Success(_)).recover { case ex => Failure(ex) }
  }
}

object AkkaFutureHelpers extends AkkaFutureHelpers

/**
 * Provides an alternative syntax for [[Future]]s that timeout without need inheritance.
 *
 * {{{
 *   FutureWithTimeout(5.seconds) {
 *     // future work
 *   }
 * }}}
 */
object FutureWithTimeout {

  def apply[A](limit: FiniteDuration)(block: => A)(implicit system: ActorSystem, ec: ExecutionContext): Future[A] = {
    AkkaFutureHelpers.withTimeout(limit, system.scheduler)(Future(block))
  }
}