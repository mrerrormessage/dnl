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
    val poller = socketManager.pollSocket(repSocket, ctrlSocket)

    while (!stopping) {
      try {
        bound = true
        poller.poll(1000)
        if (poller.pollin(0))
          server.serveResponse(serveFunction)
        else {
          ctrlSocket.recv() // We know that it's STOP - for now
          ctrlSocket.send("0")
          stopping = true
        }
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
