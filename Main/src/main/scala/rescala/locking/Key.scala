package rescala.locking

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

final class Key[InterTurn](val turn: InterTurn) {

  @volatile private[locking] var keychain: Keychain[InterTurn] = new Keychain(this)

  val id: Long = keychain.id
  override def toString: String = s"Key($id)"

  private[this] val semaphore = new Semaphore(0)

  private[locking] def continue(): Unit = semaphore.release()
  private[locking] def await(): Unit = semaphore.acquire()


  def lockKeychain[R](f: Keychain[InterTurn] => R): R = {
    while (true) {
      val oldChain = keychain
      keychain.synchronized {
        if (oldChain eq keychain) return f(oldChain)
      }
    }
    throw new AssertionError("broke out of infinite loop")
  }

  /** contains a list of all locks owned by us. */
  private[this] val heldLocks: AtomicReference[List[TurnLock[InterTurn]]] = new AtomicReference[List[TurnLock[InterTurn]]](Nil)

  private[locking] def addLock(lock: TurnLock[InterTurn]): Unit = heldLocks.updateAndGet(new UnaryOperator[List[TurnLock[InterTurn]]] {
    override def apply(t: List[TurnLock[InterTurn]]): List[TurnLock[InterTurn]] = lock :: t
  })

  private[locking] def grabLocks() = heldLocks.getAndSet(Nil)

  /** release all locks we hold or transfer them to a waiting transaction if there is one
    * holds the master lock for request */
  def releaseAll(): Unit = lockKeychain {_.release(this)}

  def reset(): Unit = lockKeychain { kc =>
    kc.release(this)
    keychain = new Keychain(this)
  }


}
