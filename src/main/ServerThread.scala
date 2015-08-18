import java.lang.InterruptedException

import Messages._

import org.zeromq.ZMQ, ZMQ.Context

class ServerThread(context: Context, address: String, serve: Server => Unit) extends Thread {

  @volatile var stopping: Boolean = false
  @volatile var bound:    Boolean = false

  def close(): Unit = {
    stopping = true
    interrupt()
  }

  override def run(): Unit = {
    val server = new Server(context, address)
    bound = true
    while (!stopping) {
      try {
        serve(server)
      } catch {
        case i: InterruptedException =>
      }
    }
    bound = false
    server.close()
  }
}
