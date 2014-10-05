package rescala.propagation

import rescala.propagation.Pulse.{Diff, NoChange}

/** A Reactive is a value type which has a dependency to other Reactives */
trait Reactive {
  final private[propagation] val level: TurnLocal[Int] = TurnLocal(0, math.max)
  
  final private[propagation] val dependants: TurnLocal[Set[Reactive]] = TurnLocal(Set(), (_, x) => x)

  /** for testing */
  def getLevel(implicit turn: Turn) = level.get

  /** called when it is this events turn to be evaluated
    * (head of the evaluation queue) */
  protected[propagation] def reevaluate()(implicit turn: Turn): EvaluationResult

  /** called to finalize the pulse value (turn commits) */
  protected[propagation] def commit(implicit turn: Turn): Unit = {
    level.commit
    dependants.commit
  }
}

/** A node that has nodes that depend on it */
trait Pulsing[+P] extends Reactive {
  final protected[this] val pulses: TurnLocal[Pulse[P]] = TurnLocal(Pulse.none, (x, _) => x)

  final def pulse(implicit turn: Turn): Pulse[P] = pulses.get

  override def commit(implicit turn: Turn): Unit = {
    pulses.commit
    super.commit
  }
}

/** a node that has a current state */
trait Stateful[+A] extends Pulsing[A] {
  pulses.commitStrategy = (_, p) => p.keep

  final def get(implicit maybe: MaybeTurn): A = maybe { getValue(_) }

  final def getValue(implicit turn: Turn): A = pulse match {
    case NoChange(Some(value)) => value
    case Diff(value, oldOption) => value
    case NoChange(None) => throw new IllegalStateException("stateful reactive has never pulsed")
  }
}


/** reevaluation strategy for static dependencies */
trait StaticReevaluation[+P] extends Pulsing[P] {
  /** side effect free calculation of the new pulse for the current turn */
  protected[propagation] def calculatePulse()(implicit turn: Turn): Pulse[P]

  final override protected[propagation] def reevaluate()(implicit turn: Turn): EvaluationResult = {
    val p = calculatePulse()
    pulses.set(p)
    EvaluationResult.Done(p.isChange)
  }
}

/** reevaluation strategy for dynamic dependencies */
trait DynamicReevaluation[+P] extends Pulsing[P] {
  private val dependencies: TurnLocal[Set[Reactive]] = TurnLocal(Set(), (_, x) => x)

  /** side effect free calculation of the new pulse and the new dependencies for the current turn */
  def calculatePulseDependencies(implicit turn: Turn): (Pulse[P], Set[Reactive])
  
  final override protected[rescala] def reevaluate()(implicit turn: Turn): EvaluationResult = {
    val (newPulse, newDependencies) = calculatePulseDependencies

    val oldDependencies = dependencies.get
    dependencies.set(newDependencies)
    val diff = EvaluationResult.DependencyDiff(newDependencies, oldDependencies)

    if (!turn.isReady(this, newDependencies)) {
      diff
    }
    else {
      pulses.set(newPulse)
      EvaluationResult.Done(newPulse.isChange, Some(diff))
    }
  }

  override def commit(implicit turn: Turn): Unit = {
    dependencies.commit
    super.commit
  }
}