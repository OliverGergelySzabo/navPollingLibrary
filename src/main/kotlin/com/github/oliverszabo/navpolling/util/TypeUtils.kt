package com.github.oliverszabo.navpolling.util

import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.util.Date

//TODO: decide what to do with other primitive types (like older java date types)
object TypeUtils {
    val dateTypes = listOf(
        Instant::class.java,
        LocalDate::class.java,
        LocalTime::class.java,
        LocalDateTime::class.java,
        OffsetTime::class.java,
        OffsetDateTime::class.java,
        Year::class.java,
        YearMonth::class.java,
        Month::class.java,
        MonthDay::class.java,
        DayOfWeek::class.java,
        ZonedDateTime::class.java,
    )

    val numericTypes = listOf(BigInteger::class.java, BigDecimal::class.java)
        .plus(PrimitiveType.values().filter { it.isNumeric }.flatMap { it.classes })

    fun isCastableTo(type: Class<*>, targetType: Class<*>): Boolean {
        return targetType.isAssignableFrom(type) || isSamePrimitiveType(type, targetType)
    }

    fun isSameType(type: Class<*>?, otherType: Class<*>?): Boolean {
        if(type == null && otherType == null) return true
        if(type == null || otherType == null) return false
        return type == otherType || isSamePrimitiveType(type, otherType)
    }

    fun isDateType(type: Class<*>): Boolean {
        return dateTypes.contains(type)
    }

    fun isNumericType(type: Class<*>): Boolean {
        return numericTypes.contains(type)
    }

    fun isSimpleType(type: Class<*>): Boolean {
        return type.isPrimitive
                || dateTypes.contains(type)
                || numericTypes.contains(type)
                || PrimitiveType.BOOLEAN.isCastableTo(type)
                || PrimitiveType.CHAR.isCastableTo(type)
                || String::class.java == type
    }

    fun isByte(type: Class<*>): Boolean {
        return PrimitiveType.BYTE.isCastableTo(type)
    }

    fun isShort(type: Class<*>): Boolean {
        return PrimitiveType.SHORT.isCastableTo(type)
    }

    fun isInt(type: Class<*>): Boolean {
        return PrimitiveType.INT.isCastableTo(type)
    }

    fun isLong(type: Class<*>): Boolean {
        return PrimitiveType.LONG.isCastableTo(type)
    }

    fun isFloat(type: Class<*>): Boolean {
        return PrimitiveType.FLOAT.isCastableTo(type)
    }

    fun isDouble(type: Class<*>): Boolean {
        return PrimitiveType.DOUBLE.isCastableTo(type)
    }

    fun isBoolean(type: Class<*>): Boolean {
        return PrimitiveType.BOOLEAN.isCastableTo(type)
    }

    private fun isSamePrimitiveType(type: Class<*>, otherType: Class<*>): Boolean {
        PrimitiveType.values().forEach { primitive ->
            if(primitive.isCastableTo(type) && primitive.isCastableTo(otherType)) {
                return true
            }
        }
        return false
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