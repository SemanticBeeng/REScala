package tests.rescala

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import rescala.turns.Engines
import rescala.{Signals, Var}
import rescala.turns.Engines.default

class LevelPropagation extends AssertionsForJUnit {

  @Test def worksOnElementsInQueue(): Unit = {
    val level0 = Var(0)
    val l1 = level0.map(_ + 1)
    val l2 = l1.map(_ + 1)
    val level3 = l2.map(_ + 1)
    val level_1_to_4 = Signals.dynamic(level0) { t =>
      if (level0(t) == 10) level3(t) else 42
    }
    var evaluatesOnlyOncePerTurn = 0
    val level_2_to_5 = Signals.lift(level0, level_1_to_4){(x, y) => evaluatesOnlyOncePerTurn += 1 ;  x + y}

    assert(level3.getLevel === 3)
    assert(level_1_to_4.getLevel === 1)
    assert(level_2_to_5.getLevel === 2)
    assert(level_2_to_5.now === 42)
    assert(evaluatesOnlyOncePerTurn === 1)

    level0.set(5)

    assert(level3.getLevel === 3)
    assert(level_1_to_4.getLevel === 1)
    assert(level_2_to_5.getLevel === 2)
    assert(level_2_to_5.now === 47)
    assert(evaluatesOnlyOncePerTurn === 2)

    level0.set(10)

    assert(level3.getLevel === 3)
    assert(level_1_to_4.getLevel === 4)
    assert(level_2_to_5.getLevel === 5)
    assert(level_2_to_5.now === 23)
    assert(evaluatesOnlyOncePerTurn === 3)


  }

  @Test def doesNotBreakStuffWhenNothingChangesBeforeDependenciesAreCorrect(): Unit = {
    val l0 = Var(0)
    val l1 = l0.map(_ + 1)
    val l2 = l1.map(_ + 1)
    val l3 = l2.map(_ + 1)
    val l1t4 = Signals.dynamic(l0) { t =>
      if (l0(t) == 10) l3(t) else 3
    }
    val l2t5 = l1t4.map(_ + 1)

    assert(l3.getLevel === 3)
    assert(l1t4.getLevel === 1)
    assert(l2t5.getLevel === 2)
    assert(l1t4.now === 3)
    assert(l2t5.now === 4)


    l0.set(10)

    assert(l3.getLevel === 3)
    assert(l1t4.getLevel === 4)
    assert(l2t5.getLevel === 5)
    assert(l1t4.now === 13)
    assert(l2t5.now === 14)

  }

  @Test def doesNotReevaluateStuffIfNothingChanges(): Unit = {
    val l0 = Var(0)
    val l1 = l0.map(_ + 1)
    val l2 = l1.map(_ + 1)
    val l3 = l2.map(_ + 1)
    val l1t4 = Signals.dynamic(l0) { t =>
      if (l0(t) == 10) l3(t) else 13
    }
    var reevals = 0
    val l2t5 = l1t4.map{v => reevals += 1; v + 1}

    assert(l3.getLevel === 3)
    assert(l1t4.getLevel === 1)
    assert(l2t5.getLevel === 2)
    assert(l1t4.now === 13)
    assert(l2t5.now === 14)
    assert(reevals === 1)


    l0.set(10)

    assert(l3.getLevel === 3)
    assert(l1t4.getLevel === 4)
    assert(l2t5.getLevel === 5)
    assert(l1t4.now === 13)
    assert(l2t5.now === 14)
    assert(reevals === 1)


  }

}
