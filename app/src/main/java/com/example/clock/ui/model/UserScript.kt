package com.example.clock.ui.model

data class UserScript(
    val name: String,
    val namespace: String,
    val match: Set<String>,
    val content: String
) {
    companion object {
        fun parse(content: String): UserScript? {

            var name: String? = null
            var nameSpace: String? = null
            val match = HashSet<String>()

            var started = false
            var idx = 0
            var hasEnd = false
            while (idx >= 0) {

                val d = content.indexOf('\n', idx)
                val line = if (d != -1) {
                    val oldIdx = idx
                    idx = d + 1
                    content.subSequence(oldIdx, d)
                } else {
                    idx = -1
                    content.subSequence(idx, content.length)
                }

                if (!line.startsWith("//")) {
                    break
                }

                if (started) {
                    if (line.contains("// ==/UserScript==")) {
                        hasEnd = true
                        break
                    }

                    val atIdx = line.indexOf('@', 1)
                    if (atIdx < 0) continue
                    val preLine = line.subSequence(atIdx, line.length)
                    val item = preLine.split(' ', '\t', limit = 2)
                    if (item.size == 2) {
                        when (item[0]) {
                            "@name" -> name = item[1].trim()
                            "@namespace" -> nameSpace = item[1].trim()
                            "@match","@include" -> match.add(item[1].trim())
                        }
                    }
                    continue
                }

                if (line.contains("// ==UserScript==")) {
                    started = true
                    continue
                }
            }

            if (name == null || nameSpace == null || match.isEmpty() || !hasEnd) {
                return null
            } else {
                return UserScript(name, nameSpace, match, content)
            }

        }
    }
}