import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Git

val Scala212 = "2.12.15"

val scalikejdbcVersion = settingKey[String]("")
val wartremoverVersion = "3.0.3"

val projectName = "wartremover-scalikejdbc"

Global / onChangedBuildSource := ReloadOnSourceChanges

val updateReadmeTask = { state: State =>
  val extracted = Project.extract(state)
  val v = extracted get version
  val org = extracted get organization
  val modules = projectName :: Nil
  val readme = "README.md"
  val readmeFile = file(readme)
  val newReadme = Predef
    .augmentString(IO.read(readmeFile))
    .lines
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
  val git = new Git(extracted get baseDirectory)
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

val unusedWarnings = Seq("-Ywarn-unused")

val crossScalaVersionSettings = Def.settings(
  crossScalaVersions := Seq(Scala212, "2.13.8", "3.1.2")
)

lazy val core = project
  .in(file("core"))
  .settings(
    name := projectName,
    crossScalaVersionSettings,
    libraryDependencies += {
      if (scalaBinaryVersion.value == "3") {
        "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion.value
      } else {
        "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion.value % "test"
      }
    },
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.3" % "test",
      "org.wartremover" %% "wartremover" % wartremoverVersion cross CrossVersion.full,
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
      s"-Dscalikejdbc.version=${scalikejdbcVersion.value}",
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
crossScalaVersionSettings

lazy val commonSettings = Def.settings(
  scalikejdbcVersion := "4.0.0",
  (Compile / unmanagedResources) += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  scalaVersion := Scala212,
  addCommandAlias("SetScala212", s"""++ ${Scala212}! -v"""),
  scalacOptions ++= {
    if (scalaBinaryVersion.value != "3") {
      unusedWarnings
    } else {
      Nil
    }
  },
  Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings),
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
  publishTo := sonatypePublishToBundle.value,
  (Compile / doc / scalacOptions) ++= {
    val t = tagOrHash.value
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/xuwei-k/wartremover-scalikejdbc/tree/${t}€{FILE_PATH}.scala"
    )
  },
  ReleasePlugin.extraReleaseCommands,
  commands += Command.command("updateReadme")(updateReadmeTask),
  releaseTagName := tagName.value,
  releaseCrossBuild := true,
  releaseProcess := Seq[ReleaseStep](
    inquireVersions,
    runClean,
    setReleaseVersion,
    commitReleaseVersion,
    updateReadmeProcess,
    tagRelease,
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted.runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
      },
      enableCrossBuild = true
    ),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    updateReadmeProcess,
    pushChanges
  )
)
