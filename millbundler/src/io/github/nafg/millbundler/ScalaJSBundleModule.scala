package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps
import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule

import mill.*
import mill.PathRef
import mill.T
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalajslib.api.Report

//noinspection ScalaWeakerAccess
trait ScalaJSBundleModule extends ScalaJSNpmModule:

  override protected def packageJson: Task[JsDeps => ujson.Obj] = Task.Anon {
    val tpe = moduleKind() match
      case ModuleKind.CommonJSModule => Some("commonjs")
      case ModuleKind.NoModule       => None
      case ModuleKind.ESModule       => Some("module")
    (deps: JsDeps) =>
      ujson.Obj.from(
        ujson.Obj(
          "name" -> moduleCtx.enclosing,
          tpe.map(ujson.Str(_)).map("type" -> _).toSeq*
        ).value.toSeq ++
          super.packageJson()(deps).value
      )
  }

  protected def getReportMainFilePath(report: Report): Iterable[os.Path] =
    report.publicModules.map(module => report.dest.path / module.jsFileName)

  def bundleFilename: T[String] = "out-bundle.js"
  def outputEntryFileNames: T[String] = "out-bundle-[name].js"

  protected def linkNpmInstall = Task.Anon {
    for path <- os.list(npmInstall().path) do
      os.remove(Task.dest / path.last)
      os.symlink(Task.dest / path.last, path)
  }

  protected def expectedBundleFilename(
      inputFile: os.Path,
      multiEntry: Boolean,
      singleFilename: String,
      entryFileNames: String
  ): String =
    if multiEntry then
      val (head, tail) = entryFileNames.split("\\[name\\]") match
        case Array(head, tail) => (head, tail)
        case _                 =>
          throw new RuntimeException(
            "Invalid output bundle file names, must contain [name]"
          )
      val name = inputFile.last.stripSuffix(".js").stripSuffix(".map")
      head + name + tail
    else singleFilename

  protected def bundlePaths = Task.Anon { (bundles: Iterable[os.Path]) =>
    val inputFiles = bundles.toSeq
    val singleFilename = bundleFilename()
    val entryFileNames = outputEntryFileNames()
    inputFiles match
      case Seq(_) =>
        List(
          PathRef(Task.dest / singleFilename),
          PathRef(Task.dest / (singleFilename + ".map"))
        )
      case _ =>
        inputFiles.map(inputFile =>
          PathRef(
            Task.dest / expectedBundleFilename(
              inputFile,
              multiEntry = true,
              singleFilename,
              entryFileNames
            )
          )
        )
    end match
  }

  protected def bundle: Task[BundleParams => Seq[PathRef]]
end ScalaJSBundleModule

//noinspection ScalaWeakerAccess
object ScalaJSBundleModule:

  // noinspection ScalaUnusedSymbol
  trait Test extends TestScalaJSModule:
    this: ScalaJSBundleModule =>

    override def fastLinkJSTest = Task {
      val report = super.fastLinkJSTest()

      val inputModules = report.publicModules.toSeq
      val inputPaths =
        inputModules.map(module => report.dest.path / module.jsFileName)
      val multiEntry = inputPaths.size > 1
      val singleFilename = bundleFilename()
      val entryFileNames = outputEntryFileNames()

      val bundles = bundle.apply()(
        BundleParams(inputPaths, opt = false)
      )
      val groupedBundles = bundles.groupBy(_.path.last.stripSuffix(".map"))

      val modules = inputModules.zip(inputPaths).map { case (orig, path) =>
        val outName = expectedBundleFilename(
          path,
          multiEntry,
          singleFilename,
          entryFileNames
        )
        val bundleGroup = groupedBundles.getOrElse(outName, Nil)
        Report.Module(
          moduleID = orig.moduleID,
          jsFileName = outName,
          sourceMapName =
            Some(outName + ".map").filter(_ => bundleGroup.size > 1),
          moduleKind = ModuleKind.NoModule
        )
      }
      Report(publicModules = modules, dest = PathRef(Task.dest))
    }

  end Test

end ScalaJSBundleModule
