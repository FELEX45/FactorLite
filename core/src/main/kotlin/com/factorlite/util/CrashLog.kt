package com.factorlite.util

import com.badlogic.gdx.Gdx
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLog {
    fun write(tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("[$tag] ${t::class.java.name}: ${t.message}")
            t.printStackTrace(pw)
            pw.flush()
            val txt = sw.toString()

            // Пишем в несколько мест, чтобы файл точно было легко найти.
            val savedTo = ArrayList<String>(3)
            runCatching {
                val fh = Gdx.files.local("crash.log")
                fh.writeString(txt, false, "UTF-8")
                savedTo += runCatching { fh.file().absolutePath }.getOrElse { fh.path() }
            }
            runCatching {
                val fh = Gdx.files.external("FactorLite/crash.log")
                fh.writeString(txt, false, "UTF-8")
                savedTo += runCatching { fh.file().absolutePath }.getOrElse { fh.path() }
            }
            runCatching {
                val f = File(System.getProperty("java.io.tmpdir"), "factorlite_crash.log")
                f.writeText(txt, Charsets.UTF_8)
                savedTo += f.absolutePath
            }

            // В консоль/логгер (иногда файл искать неудобно).
            System.err.println("FactorLite crash captured ($tag). Saved to:\n- " + savedTo.joinToString("\n- "))
            Gdx.app?.error("FactorLite", "Crash captured ($tag). Saved to: ${savedTo.joinToString(", ")}", t)
        } catch (_: Throwable) {
            // Если даже логгер упал — ничего не делаем, лишь бы не зациклиться.
        }
    }

    fun pathsHint(): String {
        val out = ArrayList<String>(3)
        runCatching {
            val fh = Gdx.files.local("crash.log")
            out += runCatching { fh.file().absolutePath }.getOrElse { fh.path() }
        }
        runCatching {
            val fh = Gdx.files.external("FactorLite/crash.log")
            out += runCatching { fh.file().absolutePath }.getOrElse { fh.path() }
        }
        runCatching {
            val f = File(System.getProperty("java.io.tmpdir"), "factorlite_crash.log")
            out += f.absolutePath
        }
        return out.joinToString("\n")
    }

    fun toText(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("${t::class.java.name}: ${t.message}")
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}

