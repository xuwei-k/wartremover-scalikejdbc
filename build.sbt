import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Git

val Scala212 = "2.12.21"

val scalikejdbcVersion = "4.3.5"
val wartremoverVersion = "3.6.0"

val projectName = "wartremover-scalikejdbc"

Global / onChangedBuildSource := ReloadOnSourceChanges

val updateReadmeTask = { (state: State) =>
  val extracted = Project.extract(state)
  val v = extracted.get(version)
  val org = extracted.get(organization)
  val modules = projectName :: Nil
  val readme = "README.md"
  val readmeFile = file(readme)
  val newReadme =
    IO.readLines(readmeFile)
      .map { line =>
        val matchReleaseOrSnapshot = line.contains("SNAPSHOT") == v.contains("SNAPSHOT")
        if (line.startsWith("libraryDependencies") && matchReleaseOrSnapshot) {
          val i = modules.map("\"" + _ + "\"").indexWhere(line.contains)
          s"""libraryDependencies += "$org" %% "${modules(i)}" % "$v""""
        } else {
          line
        }
      }
      .mkString("", "\n", "\n")
  IO.write(readmeFile, newReadme)
  val git = new Git(extracted.get(baseDirectory))
  git.add(readme) ! state.log
  git.commit(message = "update " + readme, sign = false, signOff = false) ! state.log
  sys.process.Process("git diff HEAD^") ! state.log
  state
}

val updateReadmeProcess: ReleaseStep = updateReadmeTask

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

val unusedWarnings = Def.setting(
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq("-Ywarn-unused")
    case "2.13" | "3" =>
      Seq("-Wunused:imports")
  }
)

lazy val core = projectMatrix
  .in(file("core"))
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = Seq(Scala212, "2.13.18", "3.3.8"))
  .settings(
    name := projectName,
    libraryDependencies += {
      if (scalaBinaryVersion.value == "3") {
        "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion
      } else {
        "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion % "test"
      }
    },
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      "org.wartremover" %% "wartremover" % wartremoverVersion,
    ),
    commonSettings,
  )

lazy val tests = project
  .in(file("tests"))
  .enablePlugins(ScriptedPlugin)
  .settings(
    commonSettings,
    noPublish,
    sbtTestDirectory := file("example"),
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList
      .filter(a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)),
    scriptedLaunchOpts ++= Seq(
      s"-Dscalikejdbc.version=${scalikejdbcVersion}",
      s"-Dwartremover.version=${wartremoverVersion}",
      s"-Dwartremover-scalikejdbc.version=${version.value}"
    ),
  )

lazy val noPublish = Def.settings(
  publish / skip := true,
  PgpKeys.publishLocalSigned := {},
  PgpKeys.publishSigned := {},
  publishLocal := {},
  publish := {},
  Compile / publishArtifact := false
)

noPublish
commonSettings

lazy val commonSettings = Def.settings(
  (Compile / unmanagedResources) += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  scalacOptions ++= unusedWarnings.value,
  scalacOptions ++= {
    scalaBinaryVersion.value match {
      case "2.12" | "2.13" =>
        Seq("-release:8")
      case _ if scalaVersion.value.startsWith("3.3.") =>
        Seq(
          "-release:11",
          "-Yfuture-lazy-vals",
        )
      case _ =>
        Nil
    }
  },
  Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings.value),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:existentials",
  ),
  description := "warts for scalikejdbc",
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  organization := "com.github.xuwei-k",
  (Global / pomExtra) := {
    <url>https://github.com/xuwei-k/wartremover-scalikejdbc</url>
    <scm>
      <connection>scm:git:github.com/xuwei-k/wartremover-scalikejdbc.git</connection>
      <developerConnection>scm:git:git@github.com:xuwei-k/wartremover-scalikejdbc.git</developerConnection>
      <url>github.com/xuwei-k/wartremover-scalikejdbc.git</url>
      <tag>{tagOrHash.value}</tag>
    </scm>
    <developers>
      <developer>
        <id>xuwei-k</id>
        <name>Kenji Yoshida</name>
        <url>https://github.com/xuwei-k</url>
      </developer>
    </developers>
  },
  publishTo := (if (isSnapshot.value) None else localStaging.value),
  (Compile / doc / scalacOptions) ++= {
    val t = tagOrHash.value
    if (scalaBinaryVersion.value == "3") {
      Seq(
        "-source-links:github://xuwei-k/wartremover-scalikejdbc",
        "-revision",
        t
      )
    } else {
      Seq(
        "-sourcepath",
        (LocalRootProject / baseDirectory).value.getAbsolutePath,
        "-doc-source-url",
        s"https://github.com/xuwei-k/wartremover-scalikejdbc/tree/${t}€{FILE_PATH}.scala"
      )
    }
  },
  ReleasePlugin.extraReleaseCommands,
  commands += Command.command("updateReadme")(updateReadmeTask),
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    inquireVersions,
    runClean,
    setReleaseVersion,
    commitReleaseVersion,
    updateReadmeProcess,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    releaseStepCommandAndRemaining("sonaRelease"),
    setNextVersion,
    commitNextVersion,
    updateReadmeProcess,
    pushChanges
  )
)
