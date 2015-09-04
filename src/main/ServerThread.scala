import java.lang.InterruptedException

import Messages._

import org.zeromq.{ ZMQ, ZMQException }, ZMQ.Context

import zmq.ZError.IOException

class ServerThread(socketManager: SocketManager, address: String, serveFunction: Request => Response) extends Thread {

  @volatile var stopping: Boolean = false
  @volatile var bound:    Boolean = false

  val controlAddress = "inproc://server-thread-" + getId.toString

  def close(): Unit = {
    stopping = true
    val stopSocket = socketManager.reqSocket(controlAddress)
    stopSocket.send("STOP")
    stopSocket.recv()
    stopSocket.close()
  }

  override def run(): Unit = {
    val repSocket = socketManager.repSocket(address)
    repSocket.bind()
    val ctrlSocket = socketManager.repSocket(controlAddress)
    ctrlSocket.bind()
    val server = new Server(repSocket)
    val poller =
      socketManager.poller.
        withRegistration(repSocket, () => server.serveResponse(serveFunction)).
        withRegistration(ctrlSocket, { () =>
          ctrlSocket.recv() // We know that it's STOP - for now
          ctrlSocket.send("0")
          stopping = true
        })

    while (!stopping) {
      try {
        bound = true
        poller.poll(1000)
      } catch {
        case i: InterruptedException =>
          stopping = true
        case e: Exception =>
          println(e.getClass)
          println(e.getMessage)
          throw e
      }
    }
    bound = false
    ctrlSocket.close()
    repSocket.close()
  }
}
