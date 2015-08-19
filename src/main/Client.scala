import org.nlogo.api.ExtensionException

import java.util.concurrent.TimeoutException

import Sockets.MappableSocket

import Messages._

class Client(socketManager: SocketManager) {
  def rawRequest(address: String, reporter: String): String = {
    val reqSocket = socketManager.reqSocket(address, {
      s =>
        s.setSendTimeOut(3000)
        s.setReceiveTimeOut(3000)
    })
    try {
      sendRequest(reqSocket, reporter)
      reqSocket.recv().getOrElse(throw new TimeoutException("response timeout"))
    } finally {
      reqSocket.close()
    }
  }

  def request(address: String, request: Request): Response = {
    val rawResponse = rawRequest(address, Request.toString(request))
    Response.fromString(rawResponse)
      .getOrElse(throw new ExtensionException("DNL Unrecognized message: " + rawResponse))
  }

  private def sendRequest(socket: MappableSocket[String, String], reporter: String): Unit = {
    if (! socket.send(reporter)) {
      throw new TimeoutException("unable to connect")
    }
  }
}
