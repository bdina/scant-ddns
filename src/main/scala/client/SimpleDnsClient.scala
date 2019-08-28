package client

import java.net.InetAddress
import java.util.logging.Logger

object SimpleDnsClient {

  import java.nio.ByteBuffer
  import java.util.concurrent.ThreadLocalRandom

  val logger: Logger = Logger.getLogger(this.getClass.getName)

  val DefaultServerAddress: String = "8.8.8.8"
  val DnsServerPort: Integer = 53

  def dnsServer(): InetAddress = InetAddress.getByName(DefaultServerAddress)

  def clientId(): Short = {
    val clientIdBytes = new Array[Byte](2)
    ThreadLocalRandom.current.nextBytes(clientIdBytes)
    ByteBuffer.wrap(clientIdBytes).getShort
  }
}

class SimpleDnsClient(val dnsResolver: InetAddress = SimpleDnsClient.dnsServer()) extends DnsClient {

  import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
  import java.net.{DatagramPacket, DatagramSocket}
  import java.time.Instant

  import client.SimpleDnsClient._

  private var cached_address: Option[(InetAddress,Instant)] = None

  override def query(host: String, domain: String): Option[InetAddress] = {
    /** DNS QUERY **/

    val fqdn = s"$host.$domain"

    val baos = new ByteArrayOutputStream()
    val dos = new DataOutputStream(baos)

    // *** Build a DNS Request Frame ****

    val id = clientId()
    logger.fine("dns client id: $id")

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

    val domainParts = fqdn.split("\\.")
    logger.fine(s"$fqdn has ${domainParts.length} parts")

    for (i <- 0 until domainParts.length) {
      logger.fine(s"Writing: ${domainParts(i)}")
      val domainBytes = domainParts(i).getBytes("UTF-8")
      dos.writeByte(domainBytes.length)
      dos.write(domainBytes)
    }

    // No more parts
    dos.writeByte(0x00)
    // Type 0x01 = A (Host Request)
    dos.writeShort(0x0001)
    // Class 0x01 = IN
    dos.writeShort(0x0001)

    val dnsFrame = baos.toByteArray
    logger.finer(s"Sending: ${dnsFrame.length} bytes")
    for (i <- 0 until dnsFrame.length) {
      logger.finest(() => f"0x${dnsFrame(i)}%x ")
    }

    // *** Send DNS Request Frame ***
    val dnssocket = new DatagramSocket()
    logger.finest("sending question to dns ...")
    val dnsReqPacket = new DatagramPacket(dnsFrame, dnsFrame.length, dnsResolver, DnsServerPort)
    dnssocket.send(dnsReqPacket)

    // Await response from DNS server
    val buf = new Array[Byte](1024)
    val packet = new DatagramPacket(buf, buf.length)
    dnssocket.receive(packet)

    logger.finer(s"\n\nReceived: ${packet.getLength} bytes")

    val din = new DataInputStream(new ByteArrayInputStream(buf))

    logger.fine(f"Transaction ID: 0x${din.readShort()}%x")
    logger.fine(f"Flags: 0x${din.readShort()}%x")
    logger.fine(f"Questions: 0x${din.readShort()}%x")
    logger.fine(f"Answers RRs: 0x${din.readShort()}%x")
    logger.fine(f"Authority RRs: 0x${din.readShort()}%x")
    logger.fine(f"Additional RRs: 0x${din.readShort()}%x")

    var recLen = din.readByte()
    do {
      val record = new Array[Byte](recLen)
      for (i <- 0 until recLen) {
        record(i) = din.readByte()
      }
      logger.finest(s"Record: $record")
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
      var dnsIp = ""
      for (_ <- 0 until addrLen) {
        dnsIp += f"${din.readByte() & 0xFF}%d."
      }

      logger.fine(s"DNS IP -> '$dnsIp'")

      if ("" equals dnsIp) {
        logger.severe("dns response to question was nill")
        None
      } else {
        val ip = dnsIp.substring(0, dnsIp.length - 1)
        logger.info(s"dns response to question was $ip")
        val address = InetAddress.getByName(ip)
        logger.info(s"cache dns response for $ttl seconds")
        this.cached_address = Some(address, Instant.now.plusSeconds(ttl))
        Some(address)
      }
    } else None
  }

  override def address(host: String, domain: String): Option[InetAddress] = {
    cached_address match {
      case Some((address, ttl)) =>
        if (Instant.now().isBefore(ttl)) {
          logger.fine("CACHE HIT")
          Some(address)
        } else {
          logger.fine("cached value expired - invalidating")
          this.cached_address = None
          query(host, domain)
        }
      case None => query(host, domain)
    }
  }
}