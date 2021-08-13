package io.xmake.shared

class XMakeProblem (val file: String? = null, val line: String? = "0", val column: String? = "0", val kind: String? = "error", val message: String? = "") {
}
