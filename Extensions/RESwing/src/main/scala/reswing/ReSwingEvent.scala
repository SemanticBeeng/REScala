package reswing

import rescala.{implicitEngine, Event, Evt}

import scala.language.implicitConversions

/**
 * Represents `Swing` events that are fired by the library or passed to the
 * library.
 */
sealed abstract class ReSwingEvent[T] {
  private[reswing] def toEvent: Event[T]
}

final class ReSwingEventOut[T] private[reswing]
    (initLazily: ReSwingEventOut[T] => Unit) extends ReSwingEvent[T] {
  private val event: Lazy[rescala.Evt[T]] = Lazy { Evt[T] }
  private[reswing] def toEvent = { if (!event.isDefined) initLazily(this); event() }
  private[reswing] def apply(value: T) = if (event.isDefined) event()(value)
}

final class ReSwingEventIn[T] private[reswing]
    (event: Lazy[Event[T]]) extends ReSwingEvent[T] {
  private[reswing] def toEvent = { event() }
}

final class ReSwingEventNone[T] private[reswing] extends ReSwingEvent[T] {
  private[reswing] def toEvent = null
}

object ReSwingEvent {
  /**
   * Creates an empty event (that is never fired) to be used with the library.
   */
  implicit def apply[T](value: Unit): ReSwingEventNone[T] = new ReSwingEventNone[T]

  /**
   * Wraps a Event to be used with the library.
   */
  implicit def apply[T](value: => Event[T]): ReSwingEventIn[T] = new ReSwingEventIn(Lazy { value })

  /**
   * Returns the Event representing the event.
   */
  implicit def toEvent[T](value: ReSwingEvent[T]): Event[T] = value.toEvent
}
