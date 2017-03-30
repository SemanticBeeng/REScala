package examples.demo

import java.awt.Dimension

import examples.demo.ui.{Circle, Shape, ShapesPanel}
import rescala._

import scala.swing.{MainFrame, SimpleSwingApplication, UIElement}

/**
  * We refactor nsTime and ticks into a reusable framework Clock
  * object. To prevent user errors or even malicious attempts at
  * manipulating the time value, Clock should however publish only
  * a Signal[Long], not a Var[Long]. For this purpose, we refactor
  * nsTime into private _nsTime. Var is a Subtype of Signal, so we
  * can simply publish _nsTime upcasted to a Signal[Long], thereby
  * hiding the additional API methods of Var, e.g. set(...).
  *
  * Because creating specific abstractions is simple with REScala,
  * we tend to not have a kitchen sink approach, and focus on
  * providing better building blocks instead.
  */
object GModularClockCircle extends SimpleSwingApplication {
  object Clock {
    val NanoSecond = 1000000000l

    private val _nsTime = Var(System.nanoTime())
    def tick() = _nsTime.set(System.nanoTime())
    val nsTime: Signal[Long] = _nsTime

    val ticks = nsTime.change.map{ diff => diff.to.get - diff.from.get }
  }

  val shapes = Var[List[Shape]](List.empty)
  val panel = new ShapesPanel(shapes)

  val angle = Clock.nsTime.map( _.toDouble / Clock.NanoSecond * math.Pi)

  val velocityX = Signal{ (panel.width() / 2 - 50).toDouble * math.sin(angle()) / Clock.NanoSecond }
  val velocityY = Signal{ (panel.height() / 2 - 50).toDouble * math.cos(angle()) / Clock.NanoSecond }

  val posX = Clock.ticks.fold(0d){ (pX, tick) => pX + tick.toDouble * velocityX.before }
  val posY = Clock.ticks.fold(0d){ (pY, tick) => pY + tick.toDouble * velocityY.before }

  shapes.transform(new Circle(posX.map(_.toInt), posY.map(_.toInt), Var(50)) :: _)

  override lazy val top = {
    panel.preferredSize = new Dimension(400, 300)
    new MainFrame {
      title = "REScala Demo"
      contents = panel
      setLocationRelativeTo(new UIElement { override def peer = null })
    }
  }

  override def main(args: Array[String]): Unit = {
    super.main(args)

    while(!top.visible) Thread.sleep(5)
    while(top.visible) {
      Thread.sleep(1)
      Clock.tick()
    }
  }
}
