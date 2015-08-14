import org.nlogo.api._
import org.nlogo.api.Syntax._
import org.nlogo.api.ScalaConversions._

class DistributedNetLogoExtension extends DefaultClassManager {
  def load(manager: PrimitiveManager) {
    manager.addPrimitive("info", new Info())
    manager.addPrimitive("report", new Report())
    manager.addPrimitive("command", new Command())
  }
}

class Info extends DefaultReporter {
  override def getSyntax: Syntax = Syntax.reporterSyntax(Syntax.StringType)
  override def getAgentClassString: String = "OTPL"

  override def report(args: Array[Argument], context: Context): AnyRef = {
    "foobar"
  }
}

class Report extends DefaultReporter {
  override def getSyntax: Syntax =
    Syntax.reporterSyntax(Array(Syntax.StringType, Syntax.StringType), Syntax.StringType)

  override def getAgentClassString: String = "OTPL"

  override def report(args: Array[Argument], context: Context): AnyRef = {
    false: java.lang.Boolean
  }
}

class Command extends DefaultCommand {
  override def getSyntax: Syntax =
    Syntax.commandSyntax(Array(Syntax.StringType, Syntax.StringType))

  override def getAgentClassString: String = "OTPL"

  override def perform(args: Array[Argument], context: Context): Unit = {
  }
}
