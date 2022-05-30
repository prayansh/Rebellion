object Logger {
    var level = Level.OFF

    enum class Level(val prio: Int) {
        DEBUG(1), INFO(2), ERROR(3), OFF(10000)
    }

    fun log(msg: String, level: Level) {
        if (this.level <= level) {
            console.log(msg)
        }
    }

    fun debug(msg: String, level: Level = Level.DEBUG) {
        log(msg, level)
    }

    fun info(msg: String, level: Level = Level.INFO) {
        log(msg, level)
    }

    fun error(msg: String, level: Level = Level.ERROR) {
        log(msg, level)
    }
}

@JsExport
fun enableDebugLogs() {
    Logger.level = Logger.Level.DEBUG
}
