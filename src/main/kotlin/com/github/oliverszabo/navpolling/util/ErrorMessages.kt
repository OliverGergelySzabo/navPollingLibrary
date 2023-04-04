package com.github.oliverszabo.navpolling.util

class ErrorMessages private constructor() {
    companion object {
        fun propertyMustBeGreaterThan(propertyName: String, limit: Number): String {
            return Templates.MUST_BE_GREATER_THAN_N.format(propertyName, "property", limit)
        }

        fun propertyMustBeGreaterThanOrEqualTo(propertyName: String, limit: Number): String {
            return Templates.MUST_BE_GREATER_THAN_OR_EQUAL_TO_N.format(propertyName, "property", limit)
        }

        fun paramMustBeGreaterThanOrEqualTo(paramName: String, limit: Number): String {
            return Templates.MUST_BE_GREATER_THAN_OR_EQUAL_TO_N.format(paramName, "parameter", limit)
        }
    }

    class Templates private constructor() {
        companion object {
            const val MUST_BE_GREATER_THAN_N = "The value of the '%s' %s must be than %d."
            const val MUST_BE_GREATER_THAN_OR_EQUAL_TO_N = "The value of the '%s' %s must be than or equal to %d."
        }
    }
}