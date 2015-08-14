import java.util.concurrent.TimeoutException

import org.scalatest.FunSuite
import org.scalatest.concurrent.{ Conductors, Timeouts }
import org.scalatest.time.SpanSugar._

import org.zeromq.ZMQ, ZMQ.Context

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
      reqSocket.send(toZMQBytes(reporter))
      reqSocket.setSocketOpt(ZMQ.ZMQ_RCVTIMEO, 3)
      val reply = reqSocket.recv(0)
      fromZMQBytes(reply)
    }
  }

  class Server(context: Context, address: String) extends Helpers {
    val socket = context.socket(ZMQ.REP)
    socket.bind(address)

    def serve(f: String => String) = {
      val req = socket.recv(0)
      val reqString = fromZMQBytes(req)
      socket.send(toZMQBytes(f(reqString)), 0)
      socket.close()
    }

    def close(): Unit =
      socket.close()
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

}
