import java.util.concurrent.{ Semaphore, TimeoutException }

import Messages._

import org.scalatest.{ BeforeAndAfterAll, FunSuite, OneInstancePerTest }
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.time.SpanSugar._

import org.zeromq.ZMQ, ZMQ.Context

class IntegrationTest extends FunSuite with AsyncAssertions with BeforeAndAfterAll {
  val context = ZMQ.context(1)

  override implicit def patienceConfig: PatienceConfig  = PatienceConfig(1 seconds, 50 millis)

  override def afterAll() =
    context.term()

  def thread(name: String)(f:() => Unit)(implicit w: Waiter): Thread = {
    val t = new Thread(new Runnable {
      def run(): Unit = {
        try {
          f()
        } catch {
          case e: Exception =>
            println("exception in thread " + name)
            println(e.toString)
            w { throw e }
        } finally {
          w.dismiss()
        }
      }
    })
    t.start()
    t
  }

  def withServer(name: String, f: Server => Unit)(implicit sem: Semaphore): () => Unit =
  { () =>
    val server = new Server(context, name)
    sem.release()
    f(server)
    server.close()
  }

  def withClient(f: Client => Unit)(implicit sem: Semaphore): () => Unit =
  { () =>
    val client = new Client(context)
    sem.acquire()
    f(client)
  }

  def clientServerTest(name: String, withServerCallback: Server => Unit, withClientCallback: Waiter => Client => Unit): Unit = {
    implicit val w = new Waiter
    implicit val sem = new Semaphore(0)

    val serverThread = thread("server") { withServer(name, withServerCallback) }
    val clientThread = thread("client") {
      withClient(withClientCallback(w))
    }

    w.await(dismissals(2))
  }

  test("client server with messages") {
    clientServerTest(
      "inproc://testa",
      _.serveResponse {
        case Reporter(rep) => LogoObject("53")
        case _ => fail()
      },
      { (w: Waiter) => { (client: Client) =>
          val response = client.request("inproc://testa", Reporter("count sheep"))
          w { assert(response == LogoObject("53")) }
        } }
    )
  }

  test("client return server exception") {
    clientServerTest(
      "inproc://testb",
      _.serveResponse {
        case _ => throw new Exception("problem")
      },
      { (w: Waiter) => { (client: Client) =>
        val response = client.request("inproc://testb", Reporter("count sheep"))
        w { assert(response == ExceptionResponse("problem")) }
      }}
    )
  }

  test("client returns invalid message") {
    clientServerTest(
      "inproc://testc",
      _.serveResponse {
        case _ => throw new Exception("should not get here - indicates invalid server parse of request")
      },
      { (w: Waiter) => { (client: Client) =>
        val response = client.rawRequest("inproc://testc", "foobar")
        w { assert(response == "i:foobar") }
      }}
    )
  }

  test("client submits command to server") {
    clientServerTest(
      "inproc://testd",
      _.serveResponse {
        case Command(c) => CommandComplete(c)
        case _ => throw new Exception("bad request")
      },
      { (w: Waiter) => { (client: Client) =>
        val response = client.request("inproc://testd", Command("ask turtles [die]"))
         w { assert(response == CommandComplete("ask turtles [die]")) }
      }}
      )
  }


  test("raises timeout exception when client cannot connect to server") {
    intercept[TimeoutException] {
      val client = new Client(context)
      client.rawRequest("inproc://nothere", "timeoutreq")
    }
  }

  test("raises timeout exception when client cannot connect to tcp server") {
    intercept[TimeoutException] {
      val client = new Client(context)
      client.rawRequest("tcp://0.0.0.0:10101", "timeouttcpreq")
    }
  }

  test("raises a BindException when the port cannot be bound") {
    val server = new Server(context, "inproc://a")
    intercept[Server.BindException] {
      val server2 = new Server(context, "inproc://a")
    }
    server.close()
  }

  test("bound servers have an address") {
    val server = new Server(context, "tcp://127.0.0.1:9090")
    assert(server.address == "tcp://127.0.0.1:9090")
    server.close()
  }
}
