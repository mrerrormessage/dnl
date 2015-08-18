
import org.scalatest.{ BeforeAndAfterAll, FunSuite, OneInstancePerTest }
import org.scalatest.concurrent.Timeouts
import org.scalatest.time.SpanSugar._

import org.zeromq.ZMQ, ZMQ.Context

class ServerThreadTest extends FunSuite with Timeouts with BeforeAndAfterAll {

  val context = ZMQ.context(1)

  override def afterAll() =
    context.term()

  test("That ServerThread can be stopped") {
    val st = new ServerThread(context, "inproc://test1", (server) => Thread.sleep(1000))
    st.start()

    while(! st.bound) {}

    failAfter(400 millis) {
      st.close()
      st.join(400)
    }
  }
}
