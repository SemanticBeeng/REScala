package universe



import universe.Globals.engine._

class Plant(implicit world: World) extends BoardElement {


  val energy = Var(Plant.Energy)
  val isDead = energy map (_ <= 0)
  val age: Signal[Int] = world.time.hour.changed.iterate(0)(_ + 1)
  val grows: Event[Int] = age.changed && {_ % Plant.GrowTime == 0}
  val size: Signal[Int] = grows.iterate(0)(acc => math.min(Plant.MaxSize, acc + 1))
  val expands: Event[Unit] = size.changedTo(Plant.MaxSize)
  override def isAnimal: Boolean = false


  expands += { _ => //#HDL
    // germinate: spawn a new plant in proximity to this one
    world.plan {
      world.board.getPosition(this).foreach { mypos =>
        world.board.nearestFree(mypos).foreach { target =>
          world.spawn(new Plant)
        }
      }
    }
  }
  /** takes amount away from the energy of this plant */
  def takeEnergy(amount: Int) = energy.set(energy.now - amount)
}

object Plant {
  val Energy = 100
  val GrowTime = 50
  // after how many hours plant grows (increments size)
  val MaxSize = 6 // max size a plant reaches. then expands
}

