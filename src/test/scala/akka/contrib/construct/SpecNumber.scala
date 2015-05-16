package akka.contrib.construct

import org.scalatest.{Suite, BeforeAndAfterEach}

/**
 * Provides the count of the current of the spec relative to the Suite.
 *
 * @note this is not thread-safe and does not work with parallel test runners!!
 */
private[construct] trait SpecNumber extends BeforeAndAfterEach {
  self: Suite =>

  private[this] var specNum: Int = 0

  protected def n: Int = specNum

  protected def specNumber: Int = specNum

  override protected def beforeEach(): Unit = {
    specNum += 1
    super.beforeEach()
  }
}