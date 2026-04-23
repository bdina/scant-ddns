package protocol

import java.net.InetAddress

import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import java.io.{ByteArrayOutputStream, DataOutputStream}
import client.SimpleDnsClient
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class SimpleDNSProviderSpec extends AnyFlatSpec with Matchers {
  "SimpleDnsClient.Request.Question.frame" should "encode dns header and question metadata" in {
    val frame = SimpleDnsClient.Request.Question(app.network.Host("www"), app.network.Domain("example.com")).frame()
    frame.length should be > 18
    ((frame(2) & 0xff) << 8 | (frame(3) & 0xff)) shouldBe 0x0100
    ((frame(4) & 0xff) << 8 | (frame(5) & 0xff)) shouldBe 0x0001
    frame.takeRight(4).map(_ & 0xff).toList shouldBe List(0, 1, 0, 1)
  }

  "SimpleDnsClient.Response.Question" should "parse an ipv4 address from a dns response frame" in {
    val out = new ByteArrayOutputStream()
    val data = new DataOutputStream(out)

    data.writeShort(0x1001)
    data.writeShort(0x8180)
    data.writeShort(0x0001)
    data.writeShort(0x0001)
    data.writeShort(0x0000)
    data.writeShort(0x0000)
    data.writeByte(3)
    data.write("www".getBytes("UTF-8"))
    data.writeByte(7)
    data.write("example".getBytes("UTF-8"))
    data.writeByte(3)
    data.write("com".getBytes("UTF-8"))
    data.writeByte(0)
    data.writeShort(0x0001)
    data.writeShort(0x0001)
    data.writeShort(0xc00c)
    data.writeShort(0x0001)
    data.writeShort(0x0001)
    data.writeInt(60)
    data.writeShort(4)
    data.write(Array[Byte](93, 184.toByte, 216.toByte, 34))

    val parsed = SimpleDnsClient.Response.Question(out.toByteArray)
    parsed.map(_.address) shouldBe Some(InetAddress.getByName("93.184.216.34"))
  }

  "SimpleDnsClient.clientId" should "be safe under concurrent generation" in {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val ids = ConcurrentHashMap.newKeySet[Short]()
    val parallel = 16
    val perWorker = 512

    Await.result(
      Future.sequence((0 until parallel).map { _ =>
        Future {
          (0 until perWorker).foreach { _ =>
            ids.add(SimpleDnsClient.clientId())
          }
        }
      }),
      5.seconds
    )

    ids.size() should be > (parallel * perWorker / 4)
  }
}
