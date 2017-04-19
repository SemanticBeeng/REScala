package examples.pong

import java.awt.Rectangle

import rescala._

object Racket {
  val Height = 80
  val Width = 10
}

class Racket(val xPos: Int, val yPos: Signal[Int]) {

  val boundedYPos = Signal {
    math.min(Pong.Max_Y - Racket.Height / 2,
      math.max(Racket.Height / 2,
        yPos()))
  }

  val area = Signal {
    new Rectangle(xPos - Racket.Width / 2,
      boundedYPos() - Racket.Height / 2,
      Racket.Width,
      Racket.Height)
  }
}
