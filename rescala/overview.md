---
title: Overview
sidebar: rescala
---
# Overview of REScala

## Signals

In REScala, the general form of a signal s is
Signal{expr}, where expr is a standard Scala
expression. When expr is evaluated, all Signal and Var
values it refers to are registered as dependents of s; any
subsequent change of them triggers a reevaluation of s.

```scala
	val a = Var(2)
	val b = Var(3)
	val s = Signal{ a() + b() }
	println(a.getVal,b.getVal,s.getVal) // (2,3,5)
	a()= 4
	println(a.getVal,b.getVal,s.getVal) // (4,3,7)
```
REScala signals integrate seamlessly with OO design. They are
class attributes like fields and methods. They too can have
different visibilities. Public signals are part of the class
interface: Clients can refer to them to build composite
reactive values. Conversely, private signals are only for
object-internal use.  REScala signals cannot be re-assigned
new expressions once they are initialized.


```scala
	trait Animal {
		val age: Signal[Int]
		val name: Signal[String]
		private[myPackage] val speed: Signal[Double]
	}
```

## Events
REScala supports a rich event system. Imperative events
are fired directly by the programmer.

```scala
	val e = new ImperativeEvent[Int]()
	e += { x => println(x) }
	e(10)
	e(10)
	// − output −
	10
	10
```

Declarative events, are defined as a combination of other
events. For this purpose it offers operators like e1 ||e2
(occurrence of one among e1 or e2 ), e1 &&p (e1 occurs and
the predicate p is satisfied), e1 .map(f ) (the event
obtained by applying f to e1 ). Event composition allows
one to express the application logic in a clear and
declarative way

```scala
	val e1 = new ImperativeEvent[Int]()
	val e2 = new ImperativeEvent[Int]()
	val e1 OR e2 = e1 | | e2
	e1 OR e2 += ((x: Int) => println(x))
	e1(10)
	e2(10)
	// − output −
	10
	10
```

## Conversions

Since REScala promotes a mixed OO and functional style,
it is important to manage state at the boundary between
imperative and functional fragments of applications. For
this purpose, REScala provides a set of functions for
converting events into signals and vice versa.

The following code abstract over state to count the number
of mouse clicks using the fold function.

```scala
	val click: Event[(Int, Int)] = mouse.click
	val nClick: Signal[Int] = click.fold(0)( (x, ) => x+1 )
```

The following code provides the position of the last click
combining the click event and the position signal with the
snapshot function.

```scala
	val clicked: Event[Unit] = mouse.clicked
	val position: Signal[(Int,Int)] = mouse.position
	val lastClick: Signal[(Int,Int)] = position snapshot clicked
```

# Where to Go Next

[REScala download](./download.html)  
[REScala user manual](./documentation.html)