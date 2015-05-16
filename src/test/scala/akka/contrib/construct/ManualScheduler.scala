package akka.contrib.construct

import akka.actor.{Cancellable, Scheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

/**
 * Performs scheduling only when asked for, rather than based on the given [[Duration]].
 */
class ManualScheduler extends Scheduler {

  private[this] var jobs = Map.empty[Int, Job]

  private var currentMillis: Long = 0

  private var currentJobId: Int = 0

  private def newJobId(): Int = synchronized {
    currentJobId += 1
    currentJobId
  }

  /**
   * All of the scheduled [[Job]]s.
   */
  def scheduled: Iterable[Job] = jobs.values

  final class Job(val id: Int, runnable: Runnable, start: Long, interval: Option[Long])(implicit executor: ExecutionContext)
    extends Runnable
    with Cancellable {
    private[this] var cancelled = false
    jobs += this.id -> this
    override def run(): Unit = executor.execute(runnable)
    override def isCancelled: Boolean = cancelled
    override def cancel(): Boolean = {
      val previouslyCancelled = cancelled
      cancelled = true
      jobs -= this.id
      !previouslyCancelled
    }
    private[this] val nonZeroInterval = interval.filterNot(_ == 0)
    def nextRun(millisFromStart: Long): Option[Long] = {
      if (millisFromStart >= start) {
        nonZeroInterval map { interval =>
          (millisFromStart - start) % interval match {
            case 0 => millisFromStart + interval  // lap once more
            case remainder => millisFromStart + remainder  // add remainder
          }
        }
      }
      else Some(start)
    }
  }

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)
    (implicit executor: ExecutionContext): Cancellable = {
    new Job(newJobId(), runnable, initialDelay.toMillis, Some(interval.toMillis))
  }

  override def maxFrequency: Double = 0.0

  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)
    (implicit executor: ExecutionContext): Cancellable = {
    new Job(newJobId(), runnable, delay.toMillis, None)
  }

  private def nextInterval(): Option[Long] = synchronized {
    Try(scheduled.flatMap(_.nextRun(currentMillis)).min).toOption
  }

  /**
   * Finds a time after the given millis from when the Scheduler was created at which the next job will run
   * (if there is one) and returns all jobs that run at that time.
   * 
   * @param millisFromStart the number of milliseconds after the Scheduler was created from which to search.
   * @return if there is any jobs to run after the given time, then the first time at which a job runs alongside
   *         the sequence of jobs that will run, otherwise None.
   *         The returned time will always be greater than the given `millisFromStart` parameter.
   */
  def nextJobBatch(millisFromStart: Long): Option[(Long, Seq[Job])] = {
    if (scheduled.isEmpty) None
    else {
      nextInterval() map { earliest =>
        val atEarliest = scheduled.filter(_.nextRun(millisFromStart) == Some(earliest)).toList
        (earliest, atEarliest)
      }
    }
  }

  /**
   * Forwards the internal clock the given duration, running any jobs along the way.
   *
   * @param duration how long to play the scheduler forward.
   */
  def flush(duration: FiniteDuration): Unit = synchronized {
    val end = currentMillis + duration.toMillis
    while (currentMillis < end) {
      nextJobBatch(currentMillis) match {
        case Some((nextTime, nextJobs)) =>
          currentMillis = nextTime
          nextJobs.foreach(_.run())
        case None =>
          currentMillis = end
      }
    }
  }

  /**
   * Runs all the scheduled [[Job]]s once.
   *
   * This is an escape hatch to avoid calculating the maximum time it would take to run all of
   * the scheduled jobs at least once.
   */
  def runAllOnce(): Unit = {
    for (schedule <- scheduled) {
      schedule.run()
    }
  }

  /**
   * Cancels and removes all scheduled [[Job]]s from the set.
   *
   * @return false if any [[Job]]s were already cancelled, otherwise true.
   */
  def cancelAll(): Boolean = {
    scheduled.forall(_.cancel())
  }
}
