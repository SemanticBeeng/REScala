package benchmarks.simple

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{ReadWriteLock, ReentrantReadWriteLock}

import benchmarks.{EngineParam, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.BenchmarkParams
import rescala.reactives.Var
import rescala.propagation.Turn
import rescala.engines.{Engine, Engines}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
class SingleVar[S <: rescala.graph.Struct] {

  implicit var engine: Engine[S, Turn[S]] = _

  var source: Var[Boolean, S] = _
  var current: Boolean = _
  var illegalTurn: Turn[S] = _
  var lock: ReadWriteLock = _


  @Setup
  def setup(params: BenchmarkParams, work: Workload, engineParam: EngineParam[S]) = {
    engine = engineParam.engine
    current = false
    source = engine.Var(current)
    illegalTurn = engine.plan()(identity)
    if (engineParam.engine == Engines.unmanaged) lock = new ReentrantReadWriteLock()
  }

  @Benchmark
  def write(): Unit = {
    if (lock == null) {
      current = !current
      source.set(current)
    }
    else {
      lock.writeLock().lock()
      try {
        current = !current
        source.set(current)
      }
      finally lock.writeLock().unlock()
    }
  }

  @Benchmark
  def read(): Boolean = {
    if (lock == null) {
      source.now
    }
    else {
      lock.readLock().lock()
      try {
        source.now
      }
      finally lock.writeLock().unlock()
    }
  }

  @Benchmark
  def readIllegal(): Boolean = {
    source.get(illegalTurn)
  }

}
