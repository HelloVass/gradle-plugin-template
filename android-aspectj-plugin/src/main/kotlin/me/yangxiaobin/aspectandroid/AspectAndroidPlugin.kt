package me.yangxiaobin.aspectandroid

import com.android.build.gradle.internal.pipeline.TransformTask
import me.yangxiaobin.lib.base.BasePlugin
import me.yangxiaobin.lib.ext.*
import me.yangxiaobin.lib.log.ILog
import me.yangxiaobin.lib.log.LogLevel
import me.yangxiaobin.lib.transform.AbsLegacyTransform
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class AspectAndroidPlugin : BasePlugin() {

    override val TAG: String get() = "AAP"

    private val messageHandler by lazy { MessageHandler(false) }

    private var curTransform: AbsLegacyTransform? = null

    override val myLogger: ILog
        get() = super.myLogger.copy().setLevel(LogLevel.INFO)

    override fun apply(p: Project) {
        super.apply(p)

        logI.invoke("${p.name} applied AspectAndroidPlugin.")

        configAjcClasspath()

        val aspectTransform = AspectTransform(mProject)
        curTransform = aspectTransform

        p.afterEvaluate { p.getAppExtension?.registerTransform(aspectTransform) }

        configAjcCompileTask(aspectTransform.name)
    }

    private fun configAjcClasspath() {
        mProject.configurations.create("ajc")

        mProject.dependencies.add("ajc", "org.aspectj:aspectjrt:1.9.7")
    }


    private fun configAjcCompileTask(transformName: String) {

        mProject.gradle.taskGraph.whenReady { graph: TaskExecutionGraph ->

            graph.allTasks
                .find { it.name.contains(transformName, ignoreCase = true) }
                ?.doLast { t ->

                    logI("${t.name} do last begins. >>")

                    doAjcCompilation(t as TransformTask)
                }

        }
    }

    private fun doAjcCompilation(transform: TransformTask) {

        val transformOutputPath = transform.outputs.files.asPath
        val transformOutputFile = File(transformOutputPath)

        val dirs: List<File>? = transformOutputFile.listFiles()?.filter { it.isDirectory }
        val jars: List<File>? = transformOutputFile.listFiles()?.filter { it.isJarFile() }

        if (!dirs.isNullOrEmpty()) ajcCompileDir(dirs)
        if (!jars.isNullOrEmpty()) ajcCompileJars(jars)
    }

    private fun ajcCompileDir(dirs: List<File>) {

        val t1 = System.currentTimeMillis()
        logI("  ajcCompileDir begins.")

        val aspectpath = dirs.joinToString(separator = File.pathSeparator)

        dirs.forEach { dir ->

            val destDir: String = dir.absolutePath

            val ajcJrtClasspath = mProject.configurations.getByName("ajc").asPath

            val bootclasspath: String = (mProject.getAppExtension?.bootClasspath ?: return).toPath()

            val args = arrayOf<String>(
                "-1.8",
                "-showWeaveInfo",
                "-d", destDir,
                "-inpath", destDir,
                "-aspectpath", aspectpath,
                "-classpath", ajcJrtClasspath,
                "-bootclasspath", bootclasspath,
            )

            logV("  ajcCompileDir args :${args.contentToString()}")

            Main().run(args, messageHandler)

            populateMessageHandler()
        }

        logI("  ajcCompileDir ends in ${(System.currentTimeMillis() - t1).toFormat(false)}.")
    }

    /**
     * Cause ajc can NOT output the jarfile with same name with the original one.
     *
     * 1. Rename original jar.
     * 2. Do ajc compilation.
     * 3. Rename back.
     */
    private fun ajcCompileJars(jars: List<File>) {

        val t1 = System.currentTimeMillis()
        logI("  ajcCompileJars begins")

        val prefix = "pre-ajc-"

        jars
            // 1. Rename
            .map { it.renamed("$prefix${it.name}") }
            // 2. Ajc compile
            .forEach { preJar ->

                val originalName = preJar.parent + File.separator + preJar.name.substring(prefix.length)

                val jarInpath = preJar.absolutePath

                val destDir = preJar.parent

                val classpath: String = calculateClasspath()

                val bootclasspath: String = (mProject.getAppExtension?.bootClasspath ?: return).toPath()

                val args = arrayOf<String>(
                    "-1.8",
                    "-showWeaveInfo",
                    "-d", destDir,
                    "-inpath", jarInpath,
                    "-aspectpath", destDir,
                    "-classpath", classpath,
                    "-outjar", originalName,
                    "-bootclasspath", bootclasspath,
                )

                logV("  process jarfile :${preJar.name}")
                logV("  ajcCompileJars args :${args.contentToString()}")

                Main().run(args, messageHandler)

                populateMessageHandler()

                // 3. Delete original jars.
                preJar.delete()
            }

        logI("  ajcCompileJars ends in ${(System.currentTimeMillis() - t1).toFormat(false)}")
    }

    private fun calculateClasspath(): String {

        val curVariantName = curTransform?.currentVariantName
            ?: throw IllegalArgumentException("current variant name can NOT be null.")

        val ajcJrtClasspath: String = mProject.configurations.getByName("ajc").asPath

        val kotlinCompilePath: Set<File> =
            (mProject.tasks.find { it is KotlinCompile } as? KotlinCompile)?.classpath?.toSet() ?: emptySet()

        val javaCompilePath: Set<File> =
            (mProject.tasks.find { it is JavaCompile } as? JavaCompile)?.classpath?.toSet() ?: emptySet()

        // i.e. debugRuntimeClasspath
        val runtimeClasspath: Set<File> =
            mProject.configurations.find { it.name == "${curVariantName}RuntimeClasspath" }?.toSet() ?: emptySet()

        val combinedClasspath: Set<File> = kotlinCompilePath + javaCompilePath + runtimeClasspath

        val path = combinedClasspath
            .filterNot { it.name.endsWith(".aar") }
            .toPath()

        return "$path:$ajcJrtClasspath".also { cp ->
            val neatCp = cp.split(File.pathSeparator).joinToString("\r\n") { singlePath -> singlePath.split(File.separator).last() }
            logV("calculateClasspath :$neatCp \r\n")
        }
    }


    private fun populateMessageHandler() {

        for (message: IMessage in messageHandler.getMessages(null, true)) {

            when (message.kind) {
                IMessage.ABORT, IMessage.ERROR, IMessage.FAIL -> {
                    message.thrown?.printStackTrace()
                    //logE(message.message)
                    mLogger.error(message.message)
                }
                IMessage.WARNING, IMessage.INFO, IMessage.DEBUG -> {
                    //logD(message.message)
                    mLogger.debug(message.message)
                }
                else -> {
                    //logI(message.message)
                    mLogger.info(message.message)
                }
            }
        }

    }
}
