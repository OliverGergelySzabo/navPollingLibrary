package com.github.oliverszabo.navpolling.util

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date

class TypeUtils {
    companion object {
        val dateTypes = listOf(
            Date::class.java,
            LocalDate::class.java,
            LocalDateTime::class.java,
            Instant::class.java
        )

        val numericTypes = listOf(BigInteger::class.java, BigDecimal::class.java)
            .plus(PrimitiveType.values().filter { it.isNumeric }.flatMap { it.classes })

        fun isCastableTo(type: Class<*>, targetType: Class<*>): Boolean {
            PrimitiveType.values().forEach { primitive ->
                if(primitive.isCastableTo(type) && primitive.isCastableTo(targetType)) {
                    return true
                }
            }
            return type.isAssignableFrom(targetType)
        }

        fun isSimpleType(type: Class<*>): Boolean {
            return type.isPrimitive
                    || dateTypes.contains(type)
                    || numericTypes.contains(type)
                    || PrimitiveType.BOOLEAN.isCastableTo(type)
                    || PrimitiveType.CHAR.isCastableTo(type)
                    || String::class.java == type
        }

        enum class PrimitiveType(val isNumeric: Boolean, val classes: List<Class<*>>) {
            BYTE(true, listOf(Byte::class.java, java.lang.Byte::class.java, java.lang.Byte.TYPE)),
            SHORT(true, listOf(Short::class.java, java.lang.Short::class.java, java.lang.Short.TYPE)),
            INT(true, listOf(Int::class.java, Integer::class.java, Integer.TYPE)),
            LONG(true, listOf(Long::class.java, java.lang.Long::class.java, java.lang.Long.TYPE)),
            FLOAT(true, listOf(Float::class.java, java.lang.Float::class.java, java.lang.Float.TYPE)),
            DOUBLE(true, listOf(Double::class.java, java.lang.Double::class.java, java.lang.Double.TYPE)),
            BOOLEAN(false, listOf(Boolean::class.java, java.lang.Boolean::class.java, java.lang.Boolean.TYPE)),
            CHAR(false, listOf(Char::class.java, Character::class.java, Character.TYPE));

            fun isCastableTo(targetType: Class<*>): Boolean {
                return classes.contains(targetType)
            }
        }
    }
}