import java.util.concurrent.{ Semaphore, TimeoutException }

import Messages._

import org.scalatest.{ BeforeAndAfterAll, FunSuite, OneInstancePerTest }
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.time.SpanSugar._

import org.zeromq.ZMQ, ZMQ.Context

class IntegrationTest extends FunSuite with AsyncAssertions with BeforeAndAfterAll {
  val context = ZMQ.context(1)

  val socketManager = new SocketManager(context)

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(1 seconds, 50 millis)

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
            e.printStackTrace()
            w { throw e }
        } finally {
          w.dismiss()
        }
      }
    })
    t.start()
    t
  }

  def boundServer(name: String): Server = {
    val sock = socketManager.repSocket(name)
    sock.bind()
    new Server(sock)
  }

  def withServer(name: String, f: Server => Unit)(implicit sem: Semaphore): () => Unit =
  { () =>
    val server = boundServer(name)
    sem.release()
    f(server)
    server.socket.close()
  }

  def withClient(f: Client => Unit, acquireCount: Int = 1)(implicit sem: Semaphore): () => Unit =
  { () =>
    val client = new Client(socketManager)
    sem.acquire(acquireCount)
    f(client)
  }

  def clientServerTest(name: String, withServerCallback: Server => Unit, withClientCallback: Waiter => Client => Unit): Unit = {
    implicit val w = new Waiter
    implicit val sem = new Semaphore(0)

    val serverThread = thread("server") { withServer(name, withServerCallback) }
    val clientThread = thread("client") { withClient(withClientCallback(w)) }

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

  test("client sends request to multiple servers, receives multiple responses") {
    implicit val w = new Waiter
    implicit val sem = new Semaphore(0)

    val servers = Seq(1, 2, 3, 4, 5).map { i =>
      val address = "inproc://multiserver-" + i
      thread("server-" + i.toString) {
        try {
          withServer(address, _.serveResponse {
            case Reporter(r) =>
              // sleep so that the test verifies concurrency
              Thread.sleep(i * 100)
              LogoObject(i.toString)
            case _           => throw new Exception("bad request")
          })
        } catch {
          case e: Throwable =>
            println(e)
            println(e.getMessage)
            throw e
        }
      }
    }
    val client = thread("client") {
      withClient(
        acquireCount = 5,
        f = { (client: Client) =>
          val serverAddresses = Seq(1, 2, 3, 4, 5).map(i => "inproc://multiserver-" + i)
          val responses = client.multiRequest(serverAddresses, Reporter("count turtles"))
          w {
            Seq(1, 2, 3, 4, 5).foreach(i => assert(responses.contains(LogoObject(i.toString))))
          }
        })
    }

    w.await(dismissals(6))
  }

  test("client sends request to multiple servers, receives timeouts") {
    pending
  }

  test("raises timeout exception when client cannot connect to server") {
    intercept[TimeoutException] {
      val client = new Client(socketManager)
      client.rawRequest("inproc://nothere", "timeoutreq")
    }
  }

  test("raises timeout exception when client cannot connect to tcp server") {
    intercept[TimeoutException] {
      val client = new Client(socketManager)
      client.rawRequest("tcp://0.0.0.0:10101", "timeouttcpreq")
    }
  }

  test("raises a BindException when the port cannot be bound") {
    val server = boundServer("inproc://a")
    intercept[Sockets.BindException] {
      val server2 = boundServer("inproc://a")
    }
    server.socket.close()
  }

  test("bound servers have an address") {
    val server = boundServer("tcp://127.0.0.1:9090")
    assert(server.socket.address == "tcp://127.0.0.1:9090")
    server.socket.close()
  }
}
