package protocol

import java.net.InetAddress
import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class ExternalIPProviderSpec extends AnyFlatSpec with Matchers {
  private case class StubExternalIPProvider(value: Option[InetAddress]) extends ExternalIPProvider {
    override def address(): Option[InetAddress] = value
  }

  "ExternalIPProvider.failover" should "return primary provider result when present" in {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val primary = StubExternalIPProvider(Some(InetAddress.getByName("203.0.113.1")))
    val secondary = StubExternalIPProvider(Some(InetAddress.getByName("203.0.113.2")))

    scala.concurrent.Await.result(ExternalIPProvider.failover(primary, secondary), 2.seconds) shouldBe
      Some(InetAddress.getByName("203.0.113.1"))
  }

  it should "fallback to secondary provider when primary has no result" in {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val primary = StubExternalIPProvider(None)
    val secondary = StubExternalIPProvider(Some(InetAddress.getByName("203.0.113.2")))

    scala.concurrent.Await.result(ExternalIPProvider.failover(primary, secondary), 2.seconds) shouldBe
      Some(InetAddress.getByName("203.0.113.2"))
  }
}
