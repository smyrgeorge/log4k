package io.github.smyrgeorge.log4k.impl.extensions

fun String.format(args: Array<out Any?>): String {
    var result = this
    args.forEach { arg ->
        result = result.replaceFirst("{}", arg.toString())
    }
    return result
}
