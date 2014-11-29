package rescala

import rescala.propagation.turns.Turn
import rescala.propagation.turns.creation.Engine
import rescala.propagation.{EvaluationResult, Pulse}

/**
 * An implementation of an imperative event
 */
final class Evt[T]() extends Event[T] {

  /** Trigger the event */
  def apply(v: T)(implicit fac: Engine): Unit = fac.startNew { turn =>
    turn.admit(this) {
      pulses.set(Pulse.change(v))(turn)
      true
    }
  }

  override protected[rescala] def reevaluate()(implicit turn: Turn): EvaluationResult =
    EvaluationResult.Static(changed = true)
}

object Evt {
  def apply[T]() = new Evt[T]()
}
