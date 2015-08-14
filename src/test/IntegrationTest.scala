import java.util.concurrent.TimeoutException

import org.scalatest.FunSuite
import org.scalatest.concurrent.{ Conductors, Timeouts }
import org.scalatest.time.SpanSugar._

import org.zeromq.{ ZMQ, ZMQException }, ZMQ.Context

class IntegrationTest extends FunSuite with Conductors with Timeouts {
  val context = ZMQ.context(1)

  trait Helpers {
    def fromZMQBytes(bytes: Array[Byte]): String =
      new String(bytes, 0, bytes.length - 1)

    def toZMQBytes(s: String): Array[Byte] = {
      val newString = (s + " ").getBytes
      newString(newString.length - 1) = 0
      newString
    }
  }

  class Client(context: Context) extends Helpers {
    def fetchResponse(address: String, reporter: String): String = {
      val reqSocket = context.socket(ZMQ.REQ)
      reqSocket.connect(address)
      reqSocket.setSendTimeOut(500)
      val sent = reqSocket.send(toZMQBytes(reporter))
      if (sent) {
        reqSocket.setReceiveTimeOut(500)
        Option(reqSocket.recv()).map(fromZMQBytes)
          .getOrElse(throw new TimeoutException("no response received in time"))
      }
      else {
        throw new TimeoutException("unable to establish connection")
      }
    }
  }

  class Server(context: Context, address: String) extends Helpers {
    import Server._

    val socket = context.socket(ZMQ.REP)

    try {
      socket.bind(address)
    } catch {
      case z: ZMQException =>
        throw new BindException("Unable to bind to " + address)
    }

    def serve(f: String => String) = {
      val req = socket.recv(0)
      val reqString = fromZMQBytes(req)
      socket.send(toZMQBytes(f(reqString)), 0)
    }

    def close(): Unit = {
      socket.unbind(address)
      socket.close()
    }
  }

  object Server {
    class BindException(message: String) extends RuntimeException(message)
  }


  test("simple client server") {
    val conductor = new Conductor
    import conductor._

    thread("client") {
      val client = new Client(context)
      val response = client.fetchResponse("inproc://test", "req")
      assert(response == "rep")
    }

    thread("server") {
      val s = new Server(context, "inproc://test")
      s.serve { (s) =>
        assert(s == "req")
        "rep"
      }
      s.close()
    }
  }

  test("raises timeout exception when client cannot connect to server") {
    intercept[TimeoutException] {
      val client = new Client(context)
      client.fetchResponse("inproc://nothere", "timeoutreq")
    }
  }

  test("raises timeout exception when client cannot connect to tcp server") {
    intercept[TimeoutException] {
      val client = new Client(context)
      client.fetchResponse("tcp://0.0.0.0:10101", "timeouttcpreq")
    }
  }

  test("raises a BindException when the port cannot be bound") {
    val server = new Server(context, "inproc://a")
    intercept[Server.BindException] {
      val server2 = new Server(context, "inproc://a")
    }
    server.close()
  }
}
