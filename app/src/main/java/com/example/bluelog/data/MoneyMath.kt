package com.example.bluelog.data

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

object MoneyMath {

    fun formatRupees(paise: Long): String {
        val rupees = paise / 100.0
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        if (paise % 100L == 0L) {
            format.maximumFractionDigits = 0
        } else {
            format.maximumFractionDigits = 2
        }
        return format.format(rupees)
    }

    fun evaluateExpressionToPaise(input: String): Long? {
        val cleaned = input.replace(" ", "").replace("₹", "")
        if (cleaned.isEmpty()) return null

        return try {
            val result = evaluateSimpleMath(cleaned)
            if (result.isNaN() || result.isInfinite() || result < 0) null
            else (result * 100.0).roundToLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun evaluateSimpleMath(expr: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < expr.length) expr[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expr.length) throw RuntimeException("Unexpected character")
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) || eat('×'.code) -> x *= parseFactor()
                        eat('/'.code) || eat('÷'.code) -> {
                            val divisor = parseFactor()
                            if (divisor == 0.0) throw ArithmeticException("Divide by zero")
                            x /= divisor
                        }
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = expr.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected character")
                }
                return x
            }
        }.parse()
    }
}