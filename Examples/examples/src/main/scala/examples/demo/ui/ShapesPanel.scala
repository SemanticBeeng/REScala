package examples.demo.ui;

import java.awt.event._
import java.awt.{Event => _, _}

import rescala._

import scala.swing.Panel

case class Point(x: Int, y: Int)

class ShapesPanel(val shapes: Signal[Traversable[Shape]]) extends Panel {
  //val allChanges: Event[Any] = Event { shapes().find{ shape: Shape => shape.changed().isDefined } }
  val allChanges: Event[Any] = shapes.map(_.map(_.changed)).flatten
  allChanges observe {_ => repaint() }

  override def paintComponent(g: Graphics2D): Unit = {
    implicitEngine.plan() {implicit turn =>
      g.setColor(Color.WHITE)
      g.fillRect(0, 0, size.width, size.height)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.translate(size.width / 2, size.height / 2)
      for (shape <- shapes.now) {
        shape.drawSnapshot(g)
      }
    }
  }

  val _size: Var[Dimension] = Var(size)
  val sigSize: Signal[Dimension] = _size
  peer.addComponentListener(new ComponentListener {
    override def componentShown(e: ComponentEvent) = {}
    override def componentHidden(e: ComponentEvent) = {}
    override def componentMoved(e: ComponentEvent) = {}
    override def componentResized(e: ComponentEvent) = _size.set(size)
  })

  val width = _size.map(_.width)
  val height = _size.map(_.height)

  object Mouse {
    class MouseButton {
      val pressed = Evt[Point]
      val released = Evt[Point]
      val clicked = Evt[Point]
      val state = (pressed.map(_ => true) || released.map(_ => false)).latest(false)
    }
    val _position = Var[Point](Point(0, 0))
    val x = _position.map(_.x)
    val y = _position.map(_.y)
    val wheel = Evt[Int]
    val _buttons: Array[MouseButton] = (0 until MouseInfo.getNumberOfButtons).map{_ => new MouseButton}.toArray

    def button(id: Int): MouseButton = _buttons(id - 1)
    val leftButton = button(1)
    val middleButton = button(2)
    val rightButton = button(3)

    def translatePoint(from: java.awt.Point): Point = {
      Point(from.x - size.width / 2, from.y - size.height / 2)
    }
    val listener = new MouseAdapter {
      override def mousePressed(e: MouseEvent) = button(e.getButton()).pressed.fire(translatePoint(e.getPoint))
      override def mouseReleased(e: MouseEvent) = button(e.getButton()).released.fire(translatePoint(e.getPoint))

      override def mouseMoved(e: MouseEvent) = _position.set(translatePoint(e.getPoint))
      override def mouseDragged(e: MouseEvent) = _position.set(translatePoint(e.getPoint))

      override def mouseWheelMoved(e: MouseWheelEvent) = wheel.fire(e.getScrollAmount)
    }
    peer.addMouseListener(listener)
    peer.addMouseMotionListener(listener)
    peer.addMouseWheelListener(listener)
  }
}
