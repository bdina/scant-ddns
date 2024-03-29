package client

import app.network.{Domain,Host}
import java.net.InetAddress

import scala.util.Using

object SimpleDnsClient extends app.ScantLogging {

  import java.nio.ByteBuffer
  import java.util.concurrent.ThreadLocalRandom

  val DefaultServerAddress: String = "8.8.8.8"
  val DnsServerPort: Int = 53

  val SO_TIMEOUT: Int = java.util.concurrent.TimeUnit.SECONDS.toMillis(3L).toInt

  def dnsServerAddress(): InetAddress = InetAddress.getByName(DefaultServerAddress)

  val clientIdBytes = new Array[Byte](2)
  def clientId(): Short = {
    ThreadLocalRandom.current.nextBytes(clientIdBytes)
    ByteBuffer.wrap(clientIdBytes).getShort
  }

  object Request {
    case class Question(host: Host, domain: Domain) {
      val fqdn = s"${host}.${domain}"
      val domainParts = fqdn.split("\\.")
      logger.fine(s"$fqdn has ${domainParts.length} parts")
      def frame(): Array[Byte] = {
        import java.io.{ByteArrayOutputStream, DataOutputStream}

        Using.Manager { using =>
          val baos = using(new ByteArrayOutputStream(18 + domainParts.length))
          val dos = using(new DataOutputStream(baos))

          // *** Build a DNS Request Frame ****

          val id = clientId()
          logger.fine(s"DNS client id: $id")

          // Identifier: A 16-bit identification field generated by the device that creates the DNS query.
          // It is copied by the server into the response, so it can be used by that device to match that
          // query to the corresponding reply received from a DNS server. This is used in a manner similar
          // to how the Identifier field is used in many of the ICMP message types.
          dos.writeShort(id)
          // Write Query Flags
          dos.writeShort(0x0100)
          // Question Count: Specifies the number of questions in the Question section of the message.
          dos.writeShort(0x0001)
          // Answer Record Count: Specifies the number of resource records in the Answer section of the message.
          dos.writeShort(0x0000)
          // Authority Record Count: Specifies the number of resource records in the Authority section of
          // the message. (“NS” stands for “name server”)
          dos.writeShort(0x0000)
          // Additional Record Count: Specifies the number of resource records in the Additional section of the message.
          dos.writeShort(0x0000)

          domainParts.foreach { case part =>
            logger.fine(s"Writing: ${part}")
            val domainBytes = part.getBytes("UTF-8")
            dos.writeByte(domainBytes.length)
            dos.write(domainBytes)
          }

          // No more parts
          dos.writeByte(0x00)
          // Type 0x01 = A (Host Request)
          dos.writeShort(0x0001)
          // Class 0x01 = IN
          dos.writeShort(0x0001)

          baos.toByteArray
        }.getOrElse(Array.empty)
      }
    }
  }

  object Response {
    object Tags {
      sealed class TTL
    }
    import scalaz.{@@, Tag => _Tag}
    type TTL = Long @@ Tags.TTL
    object TTL {
      def apply(time: Long) = _Tag[Long, Tags.TTL](time)
      def unapply(time: TTL) = Some(_Tag.unwrap(time))
    }
    case class Question(address: InetAddress, ttl: TTL)
    object Question {
      def apply(buf: Array[Byte]): Option[Question] = {
        import java.io.{ByteArrayInputStream, DataInputStream}

        Using(new DataInputStream(new ByteArrayInputStream(buf))) { case din =>
          logger.fine(f"Transaction ID: 0x${din.readShort()}%x")
          logger.fine(f"Flags: 0x${din.readShort()}%x")
          logger.fine(f"Questions: 0x${din.readShort()}%x")
          logger.fine(f"Answers RRs: 0x${din.readShort()}%x")
          logger.fine(f"Authority RRs: 0x${din.readShort()}%x")
          logger.fine(f"Additional RRs: 0x${din.readShort()}%x")

          var recLen = din.readByte()
          do {
            val record = din.read(new Array[Byte](recLen))
            logger.fine(s"Record: $record")
            recLen = din.readByte()
          } while (recLen > 0)

          logger.finest(f"Record Type: 0x${din.readShort()}%x")
          logger.finest(f"Class: 0x${din.readShort()}%x")

          logger.finest(f"Field: 0x${din.readShort()}%x")
          logger.finest(f"Type: 0x${din.readShort()}%x")
          logger.finest(f"Class: 0x${din.readShort()}%x")

          val ttl = din.readInt()
          logger.finest(f"TTL: 0x$ttl%x")

          val addrLen = din.readShort()
          logger.fine(f"Len: 0x$addrLen%x")

          if (addrLen > 0) {
            val dnsIp = (0 until addrLen).map { case _ => f"${din.readByte() & 0xFF}%d" }.mkString(".")

            logger.fine(s"DNS IP -> $dnsIp")

            if (dnsIp.isEmpty) {
              logger.severe("DNS response to question was nill")
              None
            } else {
              logger.info(s"DNS response to question was $dnsIp")
              Some(Question(InetAddress.getByName(dnsIp), TTL(ttl)))
            }
          } else {
            None
          }
        }.getOrElse(None)
      }
    }
  }
}

case class SimpleDnsClient(val dnsResolver: InetAddress = SimpleDnsClient.dnsServerAddress()) extends DnsClient with app.ScantLogging {

  logger.info(s"CREATED with resolver: ${dnsResolver.getHostAddress}")

  import java.net.{DatagramPacket, DatagramSocket}
  import java.time.Instant

  import client.SimpleDnsClient._

  import protocol.net._

  private var cached_address: Option[(InetAddress,Instant,Request.Question)] = None

  override def query(host: Host, domain: Domain): Option[InetAddress] = {
    def _query(host: Host
             , domain: Domain
             , question: Request.Question = Request.Question(host,domain)
             ): Option[InetAddress] = {
      val dnsFrame = question.frame()
      logger.finer(s"Sending: ${dnsFrame.length} bytes")
      logger.finest(() => dnsFrame.map { case b => f"0x${b}%x" }.mkString(" "))

      Using(new DatagramSocket()) { case socket =>
        logger.finest("sending question to DNS ...")
        val dnsReqPacket = new DatagramPacket(dnsFrame, dnsFrame.length, dnsResolver, DnsServerPort)

        val dnsIp = (for {
          _ <- socket.trySend(dnsReqPacket).toOption
          packet <- socket.tryReceive().toOption
          response = Response.Question(packet.getData)
        } yield {
          response
        }).flatten

        dnsIp.map { case response =>
          val address = response.address
          val Response.TTL(ttl) = response.ttl
          logger.info(s"cache DNS response [${address.getHostAddress}] for $ttl seconds")
          this.cached_address = Some((address, Instant.now.plusSeconds(ttl), question))
          address
        }
      }.getOrElse(None)
    }

    logger.info(s"query DNS - fetch host [${host}] of domain [${domain}]")
    cached_address.fold (_query(host,domain)) { case (address,ttl,question) =>
      if (Instant.now().isBefore(ttl)) {
        logger.fine("CACHE HIT")
        Some(address)
      } else {
        logger.fine("cached value expired - invalidating")
        this.cached_address = None
        _query(host, domain, question)
      }
    }
  }
}
