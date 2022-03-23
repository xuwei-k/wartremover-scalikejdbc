package scalikejdbc.warts

import org.wartremover.WartTraverser
import org.wartremover.WartUniverse
import scalikejdbc.TypeBinder

object ScalikejdbcDefaultZoneId extends WartTraverser {
  def apply(u: WartUniverse): u.Traverser =
    new u.Traverser(this) {
      import q.reflect.*
      override def traverseTree(tree: Tree)(owner: Symbol): Unit = {
        tree match {
          case _ if hasWartAnnotation(tree) =>
          case _ if tree.isExpr =>
            def err(method: String) = error(
              tree.pos,
              s"TypeBinder.${method} is disabled. define OverwrittenZoneId. https://github.com/scalikejdbc/scalikejdbc/commit/8938a5161a9"
            )

            tree.asExpr match {
              case '{ TypeBinder.javaTimeZonedDateTimeDefault } =>
                err("javaTimeZonedDateTimeDefault")
              case '{ TypeBinder.javaTimeOffsetDateTimeDefault } =>
                err("javaTimeOffsetDateTimeDefault")
              case '{ TypeBinder.javaTimeLocalDateDefault } =>
                err("javaTimeLocalDateDefault")
              case '{ TypeBinder.javaTimeLocalTimeDefault } =>
                err("javaTimeLocalTimeDefault")
              case '{ TypeBinder.javaTimeLocalDateTimeDefault } =>
                err("javaTimeLocalDateTimeDefault")
              case _ =>
                super.traverseTree(tree)(owner)
            }
          case _ =>
            super.traverseTree(tree)(owner)
        }
      }
    }
}
