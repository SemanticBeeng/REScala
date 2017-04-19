package rescala.twoversion

import rescala.graph.{GraphStructType, Reactive, ReadWriteValue, Struct}
import rescala.propagation.Turn


/**
  * Spore that implements both the buffered pulse and the buffering capabilities itself.
  *
  * @tparam P Pulse stored value type
  */
trait BufferedValueStruct[P, S <: Struct] extends ReadWriteValue[P, S] with Committable[S] {
  protected var current: P
  protected val transient: Boolean
  protected var owner: Turn[S] = null
  private var update: P = _

  override def set(value: P)(implicit ticket: S#Ticket[S]): Unit = {
    val turn = ticket.turn()
    assert(owner == null || owner == turn, s"buffer owned by $owner written by $turn")
    update = value
    if (owner == null) turn.schedule(this)
    owner = turn
  }
  override def get(implicit turn: S#Ticket[S]): P = { if (turn.turn() eq owner) update else current }
  override def base(implicit turn: S#Ticket[S]): P = current

  override def commit(implicit turn: Turn[S]): Unit = {
    if (!transient) current = update
    release(turn)
  }
  override def release(implicit turn: Turn[S]): Unit = {
    owner = null
  }
}

/**
  * Implementation of a struct with graph functionality and a buffered pulse storage.
  *
  * @param current Pulse used as initial value for the struct
  * @param transient If a struct is marked as transient, changes to it can not be committed (and are released instead)
  * @param initialIncoming Initial incoming edges in the struct's graph
  * @tparam P Pulse stored value type
  * @tparam R Type of the reactive values that are connected to this struct
  */
abstract class PropagationStructImpl[P, S <: Struct](override var current: P, override val transient: Boolean, initialIncoming: Set[Reactive[S]]) extends GraphStructType[S] with BufferedValueStruct[P, S] {
  protected var _incoming: Set[Reactive[S]] = initialIncoming
  protected var _outgoing: scala.collection.mutable.Map[Reactive[S], Boolean] = rescala.util.WeakHashMap.empty


  def incoming(implicit turn: Turn[S]): Set[Reactive[S]] = _incoming
  def updateIncoming(reactives: Set[Reactive[S]])(implicit turn: Turn[S]): Unit = _incoming = reactives
  override def outgoing(implicit turn: Turn[S]): Iterator[Reactive[S]] = _outgoing.keysIterator
  override def discover(reactive: Reactive[S])(implicit turn: Turn[S]): Unit = _outgoing.put(reactive, true)
  override def drop(reactive: Reactive[S])(implicit turn: Turn[S]): Unit = _outgoing -= reactive
}
