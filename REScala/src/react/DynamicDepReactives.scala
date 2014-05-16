package react

import scala.collection.mutable.ListBuffer
import react.events.Event
import react.events.ChangedEventNode

//trait FixedDepHolder extends Reactive {
//  val fixedDependents = new ListBuffer[Dependent]
//  def addFixedDependent(dep: Dependent) = fixedDependents += dep
//  def removeFixedDependent(dep: Dependent) = fixedDependents -= dep
// def notifyDependents(change: Any): Unit = dependents.map(_.dependsOnchanged(change,this))
//}

/* A node that has nodes that depend on it */
class VarSynt[T](initval: T) extends DepHolder with Var[T] {
  private[this] var value: T = initval

  def getValue = value

  def setValue(newval: T): Unit = {

    val old = value
    // support mutable values by using hashValue rather than ==
    //val hashBefore = old.hashCode
    if (old != newval) {
      ReactiveEngine.log.nodeEvaluationStarted(this)

      value = newval
      TS.nextRound // Testing
      timestamps += TS.newTs // testing

      ReactiveEngine.log.nodeEvaluationEnded(this)

      notifyDependents(value)
      ReactiveEngine.startEvaluation

    } else {
      ReactiveEngine.log.nodePropagationStopped(this)
      //DEBUG: System.err.println("DEBUG OUTPUT: no update: " + newval + " == " + value)
      timestamps += TS.newTs // testing
    }
  }

  def update(v: T) = setValue(v)

  def apply() = getValue

  def reEvaluate(): T = value

  def map[B](f: T => B): Var[B] = VarSynt(f(getValue))
}

object VarSynt {
  def apply[T](initval: T) = new VarSynt(initval)
}

/* A dependant reactive value with dynamic dependencies (depending signals can change during evaluation) */
class SignalSynt[+T](reactivesDependsOnUpperBound: List[DepHolder])(expr: SignalSynt[T] => T)
  extends DependentSignal[T] {

  def this(expr: SignalSynt[T] => T) = this(List())(expr)

  reactivesDependsOnUpperBound.map(r => { // For glitch freedom
    if (r.level >= level) level = r.level + 1
  })

  /* Initial evaluation */
  val reactivesDependsOnCurrent = ListBuffer[DepHolder]()
  private[this] var currentValue = reEvaluate()

  def getValue = currentValue

  def triggerReevaluation() = reEvaluate

  def reEvaluate(): T = {

    /* Collect dependencies during the evaluation */
    reactivesDependsOnCurrent.map(_.removeDependent(this)) // remove me from the dependencies of the vars I depend on !
    reactivesDependsOnCurrent.clear
    timestamps += TS.newTs // Testing

    // support mutable values by using hashValue rather than ==
    //val hashBefore = currentValue.hashCode
    ReactiveEngine.log.nodeEvaluationStarted(this)
    val tmp = expr(this) // Evaluation)
    ReactiveEngine.log.nodeEvaluationEnded(this)
    //val hashAfter = tmp.hashCode

    setDependOn(reactivesDependsOnCurrent)
    reactivesDependsOnCurrent.map(_.addDependent(this))

    /* Notify dependents only of the value changed */
    if (currentValue != tmp) {
      currentValue = tmp
      timestamps += TS.newTs // Testing
      notifyDependents(currentValue)
    } else {
      ReactiveEngine.log.nodePropagationStopped(this)
      timestamps += TS.newTs // Testing
    }
    tmp
  }
  override def dependsOnchanged(change: Any, dep: DepHolder) = {
    ReactiveEngine.addToEvalQueue(this)
  }

  def apply() = getValue

  def map[B](f: T => B): Signal[B] =
    SignalSynt(List(this)) { s: SignalSynt[B] => f(this(s)) }
}

/**
 * A syntactic signal
 */
object SignalSynt {

  def apply[T](reactivesDependsOn: List[DepHolder])(expr: SignalSynt[T] => T) =
    new SignalSynt(reactivesDependsOn)(expr)

  type DH = DepHolder

  def apply[T](expr: SignalSynt[T] => T): SignalSynt[T] = apply(List())(expr)
  def apply[T](r1: DH)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(List(r1))(expr)
  def apply[T](r1: DH, r2: DH)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(List(r1, r2))(expr)
  def apply[T](r1: DH, r2: DH, r3: DH)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(List(r1, r2, r3))(expr)
  def apply[T](r1: DH, r2: DH, r3: DH, r4: DH)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(List(r1, r2, r3, r4))(expr)
  def apply[T](r1: DH, r2: DH, r3: DH, r4: DH, r5: DH)(expr: SignalSynt[T] => T): SignalSynt[T] = apply(List(r1, r2, r3, r4, r5))(expr)
}
