package rescala

import rescala.interfaces.Ticket
import rescala.turns.Turn
import rescala.graph.{DynamicReevaluation, DynamicsSupport, Pulse, Reactive, StaticReevaluation}
import rescala.signals.GeneratedLift

object Signals extends GeneratedLift {

  /** creates a dynamic signal */
  def makeDynamic[T](dependencies: Set[Reactive])(expr: Turn => T)(currentTurn: Turn): Signal[T] = currentTurn.createDynamic(dependencies) {
    new Signal[T] with DynamicReevaluation[T] {
      def calculatePulseDependencies(implicit turn: Turn): (Pulse[T], Set[Reactive]) = {
        val (newValue, dependencies) = DynamicsSupport.collectDependencies(expr(turn))
        (Pulse.diffPulse(newValue, pulses.base), dependencies)
      }
    }
  }

  /** creates a signal that has dynamic dependencies (which are detected at runtime with Signal.apply(turn)) */
  def dynamic[T](dependencies: Reactive*)(expr: Turn => T)(implicit maybe: Ticket): Signal[T] = maybe(makeDynamic(dependencies.toSet)(expr)(_))

  /** creates a signal that statically depends on the dependencies with a given initial value */
  def makeStatic[T](dependencies: Set[Reactive], init: => T)(expr: (Turn, T) => T)(initialTurn: Turn) = initialTurn.create(dependencies.toSet) {
    new Signal[T] with StaticReevaluation[T] {
      pulses.current = Pulse.unchanged(init)

      override def calculatePulse()(implicit turn: Turn): Pulse[T] = {
        val currentValue = pulses.base.current.get
        Pulse.diff(expr(turn, currentValue), currentValue)
      }
    }
  }

  /** creates a signal that folds the events in e */
  def fold[E, T](e: Event[E], init: T)(f: (T, E) => T)(implicit maybe: Ticket): Signal[T] = maybe {
    makeStatic(Set(e), init) { (turn, currentValue) =>
      e.pulse(turn).fold(currentValue, f(currentValue, _))
    }(_)
  }

  /** creates a new static signal depending on the dependencies, reevaluating the function */
  def mapping[T](dependencies: Reactive*)(fun: Turn => T)(implicit maybe: Ticket): Signal[T] = maybe { initialTurn =>
    makeStatic(dependencies.toSet, fun(initialTurn))((turn, _) => fun(turn))(initialTurn)
  }

}
