import java.util.concurrent.TimeoutException

import org.scalatest.FunSuite
import org.scalatest.concurrent.{ Conductors, Timeouts }
import org.scalatest.time.SpanSugar._

import org.zeromq.ZMQ, ZMQ.Context

class IntegrationTest extends FunSuite with Conductors with Timeouts {
  val context = ZMQ.context(1)

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
