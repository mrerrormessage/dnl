import org.nlogo.api._
import org.nlogo.api.Syntax._
import org.nlogo.api.ScalaConversions._
import org.nlogo.compiler.Compiler

import org.zeromq.ZMQ, ZMQ.{ Context => ZMQContext }

import scala.util.Random

class DistributedNetLogoExtension extends DefaultClassManager {
  var context = Option.empty[ZMQContext]
  var server = Option.empty[ServerThread]

  override def load(manager: PrimitiveManager) = {
    val port = 9000 + Random.nextInt(100)

    context = Some(ZMQ.context(1))
    val address = "tcp://127.0.0.1:" + port.toString
    server = context.map(ctx => new ServerThread(ctx, address))
    val client = new Client(context.get)

    manager.addPrimitive("info", new Info(address))
    manager.addPrimitive("report", new Report(client))
    manager.addPrimitive("command", new Command(client))

    server.map(_.start())
  }

  override def unload(em: ExtensionManager) = {
    server.foreach(_.close())
    server = None
    context.foreach(_.close())
    context = None
  }
}

class Info(address: String) extends DefaultReporter {
  override def getSyntax: Syntax = Syntax.reporterSyntax(Syntax.StringType)
  override def getAgentClassString: String = "OTPL"

  override def report(args: Array[Argument], context: Context): AnyRef =
    address
}

class Report(client: Client) extends DefaultReporter {
  override def getSyntax: Syntax =
    Syntax.reporterSyntax(Array(Syntax.StringType, Syntax.StringType), Syntax.WildcardType)

  override def getAgentClassString: String = "OTPL"

  override def report(args: Array[Argument], context: Context): AnyRef = {
    val address  = args(0).getString
    val reporter = args(1).getString
    val response = client.fetchResponse(address, reporter)
    Compiler.readFromString(response, is3D = false) // no 3D support (for now)
  }
}

class Command(client: Client) extends DefaultCommand {
  override def getSyntax: Syntax =
    Syntax.commandSyntax(Array(Syntax.StringType, Syntax.StringType))

  override def getAgentClassString: String = "OTPL"

  override def perform(args: Array[Argument], context: Context): Unit = {
  }
}
