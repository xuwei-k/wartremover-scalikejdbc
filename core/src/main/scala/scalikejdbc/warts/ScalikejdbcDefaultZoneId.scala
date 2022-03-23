package scalikejdbc.warts

import org.wartremover._

/**
 * avoid `ZoneId.systemDefault`
 *
 * [[https://github.com/scalikejdbc/scalikejdbc/blob/4.0.0/scalikejdbc-core/src/main/scala/scalikejdbc/TypeBinder.scala#L147-L156]]
 */
object ScalikejdbcDefaultZoneId extends WartTraverser {
  def apply(u: WartUniverse): u.Traverser = {
    import u.universe._
    val typeBinderName = "scalikejdbc.TypeBinder"
    val names = Seq(
      "javaTimeZonedDateTimeDefault",
      "javaTimeOffsetDateTimeDefault",
      "javaTimeLocalDateDefault",
      "javaTimeLocalTimeDefault",
      "javaTimeLocalDateTimeDefault"
    ).map(TermName.apply(_))

    object MatchForbiddenNames {
      def unapply(name: Name): Boolean = names.contains(name)
    }

    new u.Traverser {
      override def traverse(tree: Tree): Unit = {
        tree match {
          // Ignore trees marked by SuppressWarnings
          case t if hasWartAnnotation(u)(t) =>
          case Select(tpt, forbidden @ MatchForbiddenNames()) if tpt.tpe.typeSymbol.fullName == typeBinderName =>
            error(u)(
              tree.pos,
              s"${typeBinderName}.${forbidden} is disabled. define OverwrittenZoneId. https://github.com/scalikejdbc/scalikejdbc/commit/8938a5161a9"
            )
          case _ =>
            super.traverse(tree)
        }
      }
    }
  }
}
