package universe


import rescala.reactives.Signals

import universe.Globals.engine._

import scala.util.Random

/**
  * A World object unites a space (Board object), time (Time object), and a random object
  * It also defines all repetitive actions, such as spawning new Animals and Plants
  */
class World(val width: Int = 100, val height: Int = 100) {

  implicit val world = this

  val board = new Board(width, height)
  val time = new Time
  val randomness = new Random(1)

  val statusString: Signal[String] = Signals.lift(board.animalsAlive, board.animalsBorn) { (a, b) =>
    s"Animals alive: $a Total born: $b"
  }
  var updates: List[() => Unit] = Nil
  def status = statusString.now
  def tick() = {
    time.tick(())
    board.removeDead()
    val pc = board.elements.par
    pc.tasksupport = Globals.taskSupport
    pc.foreach { case (pos, be) => be.doStep(pos) }
  }
  /** batch spawns n Animals and m Plants */
  def batchSpawn(nAnimals: Int, mPlants: Int): Unit = {
    for (_ <- 1 to nAnimals) spawn(newAnimal)
    for (_ <- 1 to mPlants) spawn(new Plant)
  }
  /** returns an animal at random */
  def newAnimal: Animal = newAnimal(randomness.nextBoolean(), randomness.nextBoolean())
  def newAnimal(isHerbivore: Boolean, isMale: Boolean): Animal = {
    if (isHerbivore) {
      if (isMale) new MaleHerbivore else new FemaleHerbivore
    }
    else {
      if (isMale) new MaleCarnivore else new FemaleCarnivore
    }
  }
  /** spawns the given Board element at a free random position in the world */
  def spawn(element: BoardElement): Unit = {
    spawn(element, board.randomFreePosition(randomness))
  }

  // each day, spawn a new plant
  time.day.changed += { _ => //#HDL //#IF
    plan(this spawn new Plant)
  }

  //each week, spawn a new animal
  time.week.changed += { _ => //#HDL  //#IF
    plan(this spawn newAnimal)
  }
  /** spawns the given board element at the given position */
  def spawn(element: BoardElement, pos: Pos) = board.add(element, pos)
  def plan(f: => Unit) = synchronized(updates ::= f _)
  def runPlan() = {
    val pc = updates.par
    pc.tasksupport = Globals.taskSupport
    pc.foreach { _.apply() }
    updates = Nil
  }
}
