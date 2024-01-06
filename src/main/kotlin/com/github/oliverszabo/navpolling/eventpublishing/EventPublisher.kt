package com.github.oliverszabo.navpolling.eventpublishing

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.api.annotation.IgnoredField
import com.github.oliverszabo.navpolling.api.annotation.InvoiceFieldMapping
import com.github.oliverszabo.navpolling.api.exception.ErrorOccurredInEventHandlerException
import com.github.oliverszabo.navpolling.api.exception.InvoiceMappingException
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.TypeUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*

class EventPublisher(
    val eventHandlerObject: Any,
    val eventHandlerMethod: Method,
    private val invoiceFieldFactory: InvoiceFieldFactory,
    private val xmlMapper: XmlMapper
) {
    companion object {
        const val EVENT_HANDLER_MUST_HAVE_AT_LEAST_ONE_ARGUMENT_ERROR_MESSAGE = "Event handler methods must have at least one argument"
        const val EVENT_HANDLER_CAN_HAVE_THREE_ARGUMENT_TYPES_MOST_ERROR_MESSAGE = "Event handler method can have 3 types of arguments at most"
        val EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE
            = "Event handler methods must have a custom (non-primitive) invoice type argument. Apart from that only the ${TechnicalUser::class.java} " +
                "and the ${InvoiceDirection::class.java} are allowed argument types."
        const val EVENT_HANDLER_ARGUMENTS_MUST_CONTAIN_INVOICE_TYPE_ERROR_MESSAGE
            = "Event handler methods must contain a custom (non-primitive) invoice type argument"
        const val EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE
            = "The invoice type argument of event handlers cannot be a primitive (or boxed) type nor any known standard library type with value semantics (e.g. LocalDate)"
        const val INVOICE_FIELD_MAPPING_NO_PARAMS_SUPPLIED_ERROR_MESSAGE
            = "One of the value and fieldName parameters of the @InvoiceFieldMapping annotation must be supplied"
        const val INVOICE_FIELD_MAPPING_BOTH_PARAMS_SUPPLIED_ERROR_MESSAGE
            = "Both the value and fieldName parameters of the @InvoiceFieldMapping annotation cannot be supplied at once"
    }

    private val eventHandlerMethodParameterTypes = eventHandlerMethod.parameterTypes
    private val invoiceFieldsByTargetFieldName: Map<String, InvoiceField>
    private val targetFieldsByName: Map<String, Field>
    private val invoiceParameterType: Class<*>
    val isOnlyDigestDataRequired: Boolean

    init {
        val eventHandlerMethodParameterTypeSet = eventHandlerMethodParameterTypes.toSet()
        if(eventHandlerMethodParameterTypeSet.isEmpty()) {
            throw IllegalArgumentException(EVENT_HANDLER_MUST_HAVE_AT_LEAST_ONE_ARGUMENT_ERROR_MESSAGE)
        }
        if(eventHandlerMethodParameterTypeSet.size > 3) {
            throw IllegalArgumentException(EVENT_HANDLER_CAN_HAVE_THREE_ARGUMENT_TYPES_MOST_ERROR_MESSAGE)
        }
        
        if(eventHandlerMethodParameterTypeSet.size == 2
            && !eventHandlerMethodParameterTypeSet.contains(TechnicalUser::class.java)
            && !eventHandlerMethodParameterTypeSet.contains(InvoiceDirection::class.java)) {
            throw IllegalArgumentException(EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE)
        }
        if(eventHandlerMethodParameterTypeSet.size == 3
            && (!eventHandlerMethodParameterTypeSet.contains(TechnicalUser::class.java)
                    || !eventHandlerMethodParameterTypeSet.contains(InvoiceDirection::class.java))) {
                throw IllegalArgumentException(EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE)
            }
        
        invoiceParameterType = eventHandlerMethodParameterTypeSet.find { it != TechnicalUser::class.java && it != InvoiceDirection::class.java }
            ?: throw IllegalArgumentException(EVENT_HANDLER_ARGUMENTS_MUST_CONTAIN_INVOICE_TYPE_ERROR_MESSAGE)
        if(TypeUtils.isSimpleType(invoiceParameterType)) {
            throw IllegalArgumentException(EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE)
        }

        if(invoiceParameterType == InvoiceData::class.java || invoiceParameterType == InvoiceDigest::class.java) {
            invoiceFieldsByTargetFieldName = emptyMap()
            targetFieldsByName = emptyMap()
            isOnlyDigestDataRequired = invoiceParameterType == InvoiceDigest::class.java
        } else {
            //TODO: think about making this recursive
            invoiceFieldsByTargetFieldName = invoiceParameterType
                .declaredFields
                .filter { !it.isAnnotationPresent(IgnoredField::class.java) }
                .associate { field -> Pair(field.name, invoiceFieldFactory.getInvoiceField(getInvoiceFieldName(field), field)) }
            targetFieldsByName = invoiceParameterType.declaredFields.associateBy { it.name }
            isOnlyDigestDataRequired = invoiceFieldsByTargetFieldName.all { it.value is InvoiceDigestField }
        }
    }

    fun publishInvoiceArrivedEvent(
        invoiceDigest: InvoiceDigest,
        invoiceData: InvoiceData,
        technicalUser: TechnicalUser,
        invoiceDirection: InvoiceDirection
    ) {
        val invoiceParameterArgument = when(invoiceParameterType) {
            InvoiceData::class.java -> invoiceData
            InvoiceDigest::class.java -> invoiceDigest
            else -> createInvoiceParameterArgument(
                invoiceFieldsByTargetFieldName.mapValues { (targetFieldName, invoiceField) ->
                    val value = if(invoiceField is InvoiceDigestField) {
                        invoiceField.getValue(invoiceDigest)
                    } else {
                        invoiceField.getValue(invoiceData)
                    }
                    convertValueIfNecessary(value, targetFieldsByName[targetFieldName]!!.type)
                }
            )
        }
        callEventHandler(invoiceParameterArgument, technicalUser, invoiceDirection)
    }

    fun publishInvoiceArrivedEvent(invoiceDigest: InvoiceDigest, technicalUser: TechnicalUser, invoiceDirection: InvoiceDirection) {
        if(!isOnlyDigestDataRequired) {
            throw Exception("this publisher requires the InvoiceData not just the InvoiceDigest")
        }

        val invoiceParameterArgument = if(invoiceParameterType == InvoiceDigest::class.java) {
            invoiceDigest
        } else {
            createInvoiceParameterArgument(
                invoiceFieldsByTargetFieldName.mapValues {
                    convertValueIfNecessary(it.value.getValue(invoiceDigest), targetFieldsByName[it.key]!!.type)
                }
            )
        }
        callEventHandler(invoiceParameterArgument, technicalUser, invoiceDirection)
    }

    private fun getInvoiceFieldName(field: Field): String {
        val mappingAnnotation = field.getAnnotation(InvoiceFieldMapping::class.java) ?: return field.name
        if((mappingAnnotation.fieldName == "" && mappingAnnotation.value == "")) {
            throw IllegalArgumentException(INVOICE_FIELD_MAPPING_NO_PARAMS_SUPPLIED_ERROR_MESSAGE)
        }
        if(mappingAnnotation.fieldName != "" && mappingAnnotation.value != "" && mappingAnnotation.fieldName != mappingAnnotation.value) {
            throw IllegalArgumentException(INVOICE_FIELD_MAPPING_BOTH_PARAMS_SUPPLIED_ERROR_MESSAGE)
        }
        return if(mappingAnnotation.fieldName != "") {
            mappingAnnotation.fieldName
        } else {
            mappingAnnotation.value
        }
    }

    private fun createInvoiceParameterArgument(valuesByTargetFieldName: Map<String, Any?>): Any {
        try {
            return xmlMapper.readValue(
                xmlMapper.writeValueAsString(valuesByTargetFieldName),
                invoiceParameterType
            )
        } catch (e: Throwable) {
            throw InvoiceMappingException(invoiceParameterType, e)
        }
    }

    private fun callEventHandler(invoiceParameterArgument: Any, technicalUser: TechnicalUser, invoiceDirection: InvoiceDirection) {
        val methodArguments = eventHandlerMethodParameterTypes
            .map { type ->
                when(type) {
                    InvoiceDirection::class.java -> invoiceDirection
                    TechnicalUser::class.java -> technicalUser
                    else -> invoiceParameterArgument
                }
            }
            .toTypedArray()
        try {
            eventHandlerMethod.invoke(eventHandlerObject, *methodArguments)
        } catch (e: Throwable) {
            throw ErrorOccurredInEventHandlerException(e)
        }
    }

    private fun convertValueIfNecessary(originalValue: Any?, targetType: Class<*>): Any? {
        if(originalValue == null) return null
        if(originalValue.javaClass == targetType) return originalValue
        if(TypeUtils.isNumericType(targetType)) return convertNumericValue(originalValue, targetType)
        if(TypeUtils.isDateType(targetType)) return convertDateValue(originalValue, targetType)
        return originalValue
    }

    private fun convertNumericValue(originalValue: Any, targetType: Class<*>): Any {
        // only BigDecimal and BigInteger are supported
        val bigDecimalValue = when(originalValue.javaClass) {
            BigDecimal::class.java -> (originalValue as BigDecimal)
            BigInteger::class.java -> (originalValue as BigInteger).toBigDecimal()
            else -> throw Exception("not possible")
        }
        return when {
            targetType == BigDecimal::class.java -> bigDecimalValue
            targetType == BigInteger::class.java -> bigDecimalValue.toBigInteger()
            TypeUtils.isLong(targetType) -> bigDecimalValue.toLong()
            TypeUtils.isInt(targetType) -> bigDecimalValue.toInt()
            TypeUtils.isShort(targetType) -> bigDecimalValue.toShort()
            TypeUtils.isByte(targetType) -> bigDecimalValue.toByte()
            TypeUtils.isFloat(targetType) -> bigDecimalValue.toFloat()
            TypeUtils.isDouble(targetType) -> bigDecimalValue.toDouble()
            else -> throw Exception("not possible")
        }
    }

    private fun convertDateValue(fieldValue: Any, targetType: Class<*>): Any {
        val zonedDateTimeValue = when(fieldValue.javaClass) {
            // only Instant and LocalDate are supported
            //TODO: resolve time zone issues (either always use UTC or CET/CEST + setting for time zone provider??)
            Instant::class.java -> (fieldValue as Instant).atZone(ZoneId.systemDefault())
            LocalDate::class.java -> (fieldValue as LocalDate).atStartOfDay(ZoneId.systemDefault())
            else -> throw Exception("not possible")

        }
        return when(targetType) {
            Instant::class.java -> zonedDateTimeValue.toInstant()
            LocalDate::class.java -> zonedDateTimeValue.toLocalDate()
            LocalTime::class.java -> LocalTime.from(zonedDateTimeValue)
            LocalDateTime::class.java -> zonedDateTimeValue.toLocalDateTime()
            OffsetTime::class.java -> OffsetTime.from(zonedDateTimeValue)
            OffsetDateTime::class.java -> zonedDateTimeValue.toOffsetDateTime()
            Year::class.java -> Year.from(zonedDateTimeValue)
            YearMonth::class.java -> YearMonth.from(zonedDateTimeValue)
            Month::class.java -> Month.from(zonedDateTimeValue)
            MonthDay::class.java -> MonthDay.from(zonedDateTimeValue)
            DayOfWeek::class.java -> DayOfWeek.from(zonedDateTimeValue)
            ZonedDateTime::class.java -> zonedDateTimeValue
            else -> throw Exception("not possible")
        }
    }
}