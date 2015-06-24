package rescala.synchronization

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

import rescala.graph.Globals
import rescala.graph.ParRPState
import rescala.turns.Turn

import scala.annotation.tailrec

final class Key(val turn: Turn[ParRPState.type]) {

  val id = Globals.nextID()
  override def toString: String = s"Key($id)"

  @volatile var keychain: Keychain = new Keychain(this)

  private[this] val semaphore = new Semaphore(0)

  def continue(): Unit = semaphore.release()
  def await(): Unit = semaphore.acquire()


  def lockKeychain[R](f: => R): R = {
    @tailrec def loop(): R = {
      val oldChain = keychain
      keychain.synchronized {
        if (oldChain eq keychain) Some(f)
        else None
      } match {
        case None => loop()
        case Some(r) => r
      }
    }
    loop()
  }

  /** contains a list of all locks owned by us. */
  private[this] val heldLocks = new AtomicReference[List[TurnLock]](Nil)

  @tailrec
  def addLock(lock: TurnLock): Unit = {
    val old = heldLocks.get()
    if (!heldLocks.compareAndSet(old, lock :: old)) addLock(lock)
  }

  def grabLocks(): List[TurnLock] = heldLocks.getAndSet(Nil)

  /** release all locks we hold or transfer them to a waiting transaction if there is one
    * holds the master lock for request */
  def releaseAll(): Unit = lockKeychain {keychain.release(this)}

}