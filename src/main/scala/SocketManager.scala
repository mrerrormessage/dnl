package org.nlogo.extensions.dnl

import
  org.zeromq.{ ZMQ, ZMQException },
    ZMQ.{ Context, Poller, Socket => ZMQSocket }

import
  Sockets._

class SocketManager(context: Context) {
  def socket(socketType: Int, f: ZMQSocket => Unit): ZMQSocket = {
    val socket = context.socket(socketType)
    socket.setLinger(0)
    f(socket)
    socket
  }

  def reqSocket(address: String, f: ZMQSocket => Unit = (s => ())): MappableSocket[String, String] =
    new MappableSocket[String, String](new ReqSocketLifecycle(socket(ZMQ.REQ, f), address), fromZMQBytes, toZMQBytes)

  def repSocket(address: String, f: ZMQSocket => Unit = (s => ())): BindableSocket[String, String] =
    new BindableSocket(new RepSocketLifecycle(socket(ZMQ.REP, f), address), address, fromZMQBytes, toZMQBytes)

  def poller: DNLPoller =
    new DNLPoller(new ZMQ.Poller(_))

}
