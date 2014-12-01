package rescala

import rescala.propagation.{Ticket, Stateful}


trait Signal[+A] extends Stateful[A] {

  /** Return a Signal with f applied to the value */
  def map[B](f: A => B)(implicit maybe: Ticket): Signal[B] = Signals.mapping(this) { turn => f(get(turn)) }

  /** flatten the inner signal */
  def flatten[B]()(implicit ev: A <:< Signal[B], maybe: Ticket) = Signals.dynamic(this) { s => this(s)(s) }

  /** Return a Signal that gets updated only when e fires, and has the value of this Signal */
  def snapshot(e: Event[_])(implicit maybe: Ticket): Signal[A] = e.snapshot(this)

  /** Switch to (and keep) event value on occurrence of e */
  def switchTo[U >: A](e: Event[U])(implicit maybe: Ticket): Signal[U] = e.switchTo(this)

  /** Switch to (and keep) event value on occurrence of e */
  def switchOnce[V >: A](e: Event[_])(newSignal: Signal[V])(implicit maybe: Ticket): Signal[V] = e.switchOnce(this, newSignal)

  /** Switch back and forth between this and the other Signal on occurrence of event e */
  def toggle[V >: A](e: Event[_])(other: Signal[V])(implicit maybe: Ticket): Signal[V] = e.toggle(this, other)

  /** Delays this signal by n occurrences */
  def delay(n: Int)(implicit maybe: Ticket): Signal[A] = maybe { implicit turn => changed.delay(get, n) }

  /** Unwraps a Signal[Event[E]] to an Event[E] */
  def unwrap[E](implicit evidence: A <:< Event[E], maybe: Ticket): Event[E] = Events.wrapped(this.map(evidence))

  /**
   * Create an event that fires every time the signal changes. It fires the tuple
   * (oldVal, newVal) for the signal. The first tuple is (null, newVal)
   */
  def change(implicit maybe: Ticket): Event[(A, A)] = Events.change(this)

  /**
   * Create an event that fires every time the signal changes. The value associated
   * to the event is the new value of the signal
   */
  def changed(implicit maybe: Ticket): Event[A] = change.map(_._2)

  /** Convenience function filtering to events which change this reactive to value */
  def changedTo[V](value: V)(implicit maybe: Ticket): Event[Unit] = (changed && { _ == value }).dropParam

}