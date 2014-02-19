import sbt._
import Keys._

import com.typesafe.web.sbt.WebPlugin
import com.typesafe.web.sbt.WebPlugin.WebKeys
import com.typesafe.jse.sbt.JsEnginePlugin
import com.typesafe.jshint.sbt.JSHintPlugin
import com.typesafe.less.sbt.LessPlugin

object SharedUiPlugin extends Plugin {

  object SharedUiKeys {
    // public
    val lessFilter = SettingKey[Option[FileFilter]]("shared-less-filter")

    // internal
    val generateWebResources = TaskKey[Seq[File]]("generate-web-resources")
    val copySharedWebResources = TaskKey[Seq[File]]("copy-shared-web-resources")
  }

  import SharedUiKeys._
  import LessPlugin.LessKeys

  /** Default settings for any project that creates web assets */
  val uiSettings = WebPlugin.webSettings ++
    JsEnginePlugin.jsEngineSettings ++
    JSHintPlugin.jshintSettings ++
    lessBaseSettings ++
    Seq(
      generateWebResourcesTask,
      copySharedWebResources := Nil,
      resourceGenerators in Compile <+= generateWebResources,
      generateWebResources <<= generateWebResources.dependsOn(copyResources in WebKeys.Assets),
      compile in Compile <<= (compile in Compile).dependsOn(generateWebResources)
    )

  /** Settings for dependent UI projects
    * Causes assets generated by sharedProject to be copied into
    * the dependent project's `/public/[namespace]` resource directory.
    *
    * @param sharedProject  the SBT project that generates shared web assets
    * @param namespace      the resource subdirectory to place the shared resources; defaults to "shared"
    */
  def uses(sharedProject: Project, namespace: String = "shared"): Seq[Def.Setting[_]] =
    Seq(
      copySharedWebResources ++= {
        val baseDirectories = (resourceManaged in sharedProject in WebKeys.Assets).value :: Nil
        val newBase = (resourceManaged in WebKeys.Assets).value / namespace
        copyWebResources(baseDirectories, newBase)
      },
      copySharedWebResources <<= copySharedWebResources.dependsOn(compile in Compile in sharedProject),
      generateWebResources <<= generateWebResources.dependsOn(copySharedWebResources)
    )

  /** Resource generator that puts all web assets into managed resources */
  private def generateWebResourcesTask = generateWebResources := {
    val public = (resourceManaged in WebKeys.Assets).value
    val baseDirectories = public :: Nil
    val newBase = (resourceManaged in Compile).value / "public"
    copyWebResources(baseDirectories, newBase)
  }

  /** bases Less CSS settings to be applied to all UI projects */
  private val lessBaseSettings = LessPlugin.lessSettings ++ Seq(
    SharedUiKeys.lessFilter := None,
    LessKeys.lessFilter := {
      SharedUiKeys.lessFilter.value match {
        case Some(filter) => filter
        case None         => (LessKeys.lessFilter in WebKeys.Assets).value
      }
    }
  )

  /** Helper that walks the directory tree and returs list of files only */
  private def filesOnly(source: File): Seq[File] =
    if (!source.isDirectory) source :: Nil
    else Option(source.listFiles) match {
      case None        => Nil
      case Some(files) => files flatMap filesOnly
    }

  /** Helper for copying web resource files */
  private def copyWebResources(baseDirectories: Seq[File], newBase: File) = {
    baseDirectories foreach { _.mkdirs() }
    newBase.mkdirs()
    val sourceFiles = baseDirectories flatMap filesOnly
    val mappings = sourceFiles pair rebase(baseDirectories, newBase)
    IO.copy(mappings, true).toSeq
  }

}
