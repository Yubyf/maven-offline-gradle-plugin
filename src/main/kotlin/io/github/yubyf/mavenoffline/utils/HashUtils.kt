package io.github.yubyf.mavenoffline.utils

import java.io.File
import java.security.MessageDigest

object MessageDigestAlgorithm {
    const val MD2 = "MD2"
    const val MD5 = "MD5"
    const val SHA_1 = "SHA-1"
    const val SHA_224 = "SHA-224"
    const val SHA_256 = "SHA-256"
    const val SHA_384 = "SHA-384"
    const val SHA_512 = "SHA-512"
    const val SHA_512_224 = "SHA-512/224"
    const val SHA_512_256 = "SHA-512/256"
    const val SHA3_224 = "SHA3-224"
    const val SHA3_256 = "SHA3-256"
    const val SHA3_384 = "SHA3-384"
    const val SHA3_512 = "SHA3-512"
}

fun File.checkSum(algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val buffer = ByteArray(1024)
    inputStream().use {
        var read = it.read(buffer, 0, buffer.size)
        while (read > -1) {
            digest.update(buffer, 0, read)
            read = it.read(buffer, 0, buffer.size)
        }
    }
    return digest.digest().joinToString("") {
        String.format("%02x", it)
    }
}