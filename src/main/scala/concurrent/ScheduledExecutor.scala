package concurrent

import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent._

import scala.concurrent.{Promise,Future}

case class ScheduledExecutionContext(corePoolSize: Int = Runtime.getRuntime.availableProcessors
                                   , threadFactory: ThreadFactory = Executors.defaultThreadFactory
                                   , rejectedHandler: RejectedExecutionHandler = ScheduledExecutionContext.defaultRejectedHandler) {
  import scala.concurrent.duration.Duration
  import scala.util.Try

  private val execSrvc: ScheduledExecutorService = new ScheduledThreadPoolExecutor(corePoolSize, threadFactory, rejectedHandler)

  def schedule[T](by: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.schedule(new Runnable {
      override def run(): Unit = promise.complete(Try(operation))
    }, by.length, by.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
  def scheduleAtFixedRate[T](period: Duration, initialDelay: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = operation
    }, initialDelay.toMillis, period.length, period.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
  def scheduleWithFixedDelay[T](delay: Duration, initialDelay: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = operation
    }, initialDelay.toMillis, delay.length, delay.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
}
object ScheduledExecutionContext {
  private val defaultRejectedHandler: RejectedExecutionHandler = new AbortPolicy
}

trait CancellableFuture[T] {
  def future: Future[T]
  def cancel(mayInterruptIfRunning: Boolean): Boolean
}
object CancellableFuture {
  import scala.language.implicitConversions
  implicit def extractFuture[T](cf: CancellableFuture[T]): Future[T] = cf.future
}

private case class DelegatingCancellableFuture[T](val future: Future[T], cancelMethod: (Boolean) => Boolean) extends CancellableFuture[T] {
  def cancel(interruptIfRunning: Boolean): Boolean = cancelMethod(interruptIfRunning)
}
