package rescala.reactives

import rescala.engines.Ticket
import rescala.graph._
import rescala.propagation.Turn

trait Observe[S <: Struct] {
  def remove()(implicit maybe: Ticket[S]): Unit
}


object Observe {

  private class Obs[T, S <: Struct](bud: S#SporeP[T, Reactive[S]], dependency: Pulsing[T, S], fun: T => Unit) extends Base[T, S](bud) with Reactive[S] with Observe[S] {
    override protected[rescala] def reevaluate()(implicit turn: Turn[S]): ReevaluationResult[S] = {
      turn.schedule(once(this, dependency.pulse.toOption, fun))
      ReevaluationResult.Static(changed = false)
    }
    override def remove()(implicit maybe: Ticket[S]): Unit = maybe(_.drop(this)(dependency))
  }

  def apply[T, S <: Struct](dependency: Pulsing[T, S])(fun: T => Unit)(implicit maybe: Ticket[S]): Observe[S] = {
    val incoming = Set[Reactive[S]](dependency)
    maybe(initTurn => initTurn.create(incoming) {
      val obs = new Obs(initTurn.bufferFactory.bud[T, Reactive[S]](initialIncoming = incoming), dependency, fun)
      initTurn.schedule(once(obs, dependency.pulse(initTurn).keep.current, fun))
      obs
    })
  }


  def once[V](self: AnyRef, value: Option[V], f: V => Unit): Committable = new Committable {
    override def release(implicit turn: Turn[_]): Unit = ()
    override def commit(implicit turn: Turn[_]): Unit = value.foreach(v => turn.observe(f(v)))
    override def equals(obj: scala.Any): Boolean = self.equals(obj)
    override def hashCode(): Int = self.hashCode()
  }


}
