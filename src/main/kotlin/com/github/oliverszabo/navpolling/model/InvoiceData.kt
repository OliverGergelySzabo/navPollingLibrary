package com.github.oliverszabo.navpolling.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

class InvoiceData(
    val invoiceNumber: String,
    val invoiceIssueDate: LocalDate,
    val completenessIndicator: Boolean,
    val invoiceMain: InvoiceMain
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvoiceData
        if (invoiceNumber != other.invoiceNumber) return false
        if (invoiceMain.invoice.invoiceHead.supplierInfo.supplierTaxNumber != other.invoiceMain.invoice.invoiceHead.supplierInfo.supplierTaxNumber) return false
        return true
    }

    override fun hashCode(): Int {
        var result = invoiceNumber.hashCode()
        result = 31 * result + invoiceMain.invoice.invoiceHead.supplierInfo.supplierTaxNumber.hashCode()
        return result
    }

    override fun toString(): String {
        return "${this::class.simpleName}(invoiceNumber=$invoiceNumber, " +
                "supplierTaxNumber=${invoiceMain.invoice.invoiceHead.supplierInfo.supplierTaxNumber.taxpayerId})"
    }

    class InvoiceMain(
        val invoice: Invoice
    )

    class Invoice(
        val invoiceHead: InvoiceHead,
        val invoiceLines: InvoiceLines ?= null,

        @field:JacksonXmlProperty(localName = "productFeeSummary")
        val productFeeSummaries: List<ProductFeeSummary> ?= null,
        val invoiceSummary: InvoiceSummary
    )

    class InvoiceLines(
        val mergedItemIndicator: Boolean,
        
        @field:JacksonXmlProperty(localName = "line")
        val lines: List<Line>,
    )

    class Line(
        val lineNumber: BigInteger,
        val lineModificationReference: LineModificationReference? = null,

        @field:JacksonXmlProperty(localName = "referencesToOtherLine")
        val referencesToOtherLines: List<BigInteger>? = null,
        val advanceData: AdvanceData? = null,

        @field:JacksonXmlProperty(localName = "productCode")
        val productCodes: List<ProductCode>? = null,
        val lineExpressionIndicator: Boolean,
        val lineNatureIndicator: String? = null,
        val lineDescription: String? = null,
        val quantity: BigDecimal? = null,
        val unitOfMeasure: String? = null,
        val unitOfMeasureOwn: String? = null,
        val unitPrice: BigDecimal? = null,
        val unitPriceHUF: BigDecimal? = null,
        val lineDiscountData: DiscountData? = null,
        val lineAmountsNormal: LineAmountsNormal? = null,
        val lineAmountsSimplified: LineAmountsSimplified? = null,
        val intermediatedService: Boolean? = null,
        val aggregateInvoiceLineData: AggregateInvoiceLineData? = null,
        val newTransportMean: NewTransportMean? = null,
        val depositIndicator: Boolean? = null,
        val obligatedForProductFee: Boolean ?= null,
        val GPCExcise: BigDecimal? = null,
        val dieselOilPurchase: DieselOilPurchase? = null,
        val netaDeclaration: Boolean? = null,
        val productFeeClause: ProductFeeClause? = null,
        val lineProductFeeContent: ProductFeeData? = null,
        val conventionalLineInfo: ConventionalInvoiceInfo? = null,
        val additionalLineData: AdditionalData? = null,
    )

    class LineModificationReference(
        val lineNumberReference: BigInteger,
        val lineOperation: String
    )

    class AdvanceData(
        val advanceIndicator: Boolean,
        val advancePaymentData: AdvancePaymentData? = null
    )

    class AdvancePaymentData(
        val advanceOriginalInvoice: String,
        val advancePaymentDate: LocalDate,
        val advanceExchangeRate: BigDecimal
    )

    class ProductCode(
        val productCodeCategory: String,
        val productCodeValue: String? = null,
        val productCodeOwnValue: String? = null,
    )

    class DiscountData(
        val discountDescription: String? = null,
        val discountValue: BigDecimal? = null,
        val discountRate: BigDecimal? = null,
    )

    class LineAmountsNormal(
        val lineNetAmountData: LineNetAmountData,
        val lineVatRate: VatRate,
        val lineVatData: LineVatData ?= null,
        val lineGrossAmountData: LineGrossAmountData? = null
    )

    class LineNetAmountData(
        val lineNetAmount: BigDecimal,
        val lineNetAmountHUF: BigDecimal,
    )

    class VatRate(
        val vatPercentage: BigDecimal? = null,
        val vatContent: BigDecimal? = null,
        val vatExemption: DetailReason? = null,
        val vatOutOfScope: DetailReason? = null,
        val vatDomesticReverseCharge: Boolean? = null,
        val marginSchemeIndicator: String? = null,
        val vatAmountMismatch: VatAmountMismatch? = null,
        val noVatCharge: Boolean? = null,
    )

    class DetailReason(
        val case: String,
        val reason: String
    )

    class VatAmountMismatch(
        val vatRate: BigDecimal,
        val case: String
    )

    class LineVatData(
        val lineVatAmount: BigDecimal,
        val lineVatAmountHUF: BigDecimal,
    )

    class LineGrossAmountData(
        val lineGrossAmountNormal: BigDecimal,
        val lineGrossAmountNormalHUF: BigDecimal

    )

    class LineAmountsSimplified(
        val lineVatRate: VatRate,
        val lineGrossAmountSimplified: BigDecimal,
        val lineGrossAmountSimplifiedHUF: BigDecimal
    )

    class AggregateInvoiceLineData(
        val lineExchangeRate: BigDecimal? = null,
        val lineDeliveryDate: LocalDate
    )

    class NewTransportMean(
        val brand: String ?= null,
        val serialNum: String ?= null,
        val engineNum: String ?= null,
        val firstEntryIntoService: String ?= null,
        val vehicle: Vehicle ?= null,
        val vessel: Vessel? = null,
        val aircraft: Aircraft? = null,
    )

    class Vehicle(
        val engineCapacity: BigDecimal,
        val enginePower: BigDecimal,
        val kms: BigDecimal
    )

    class Vessel(
        val length: BigDecimal,
        val activityReferred: Boolean,
        val sailedHours: BigDecimal
    )

    class Aircraft(
        val takeOffWeight: BigDecimal,
        val airCargo: Boolean,
        val operationHours: BigDecimal
    )

    class DieselOilPurchase(
        val purchaseLocation: SimpleAddress,
        val purchaseDate: LocalDate,
        val vehicleRegistrationNumber: String,
        val dieselOilQuantity: BigDecimal ?= null
    )

    class ProductFeeClause(
        val productFeeTakeoverData: ProductFeeTakeoverData? = null,
        val customerDeclaration: CustomerDeclaration? = null
    )

    class ProductFeeTakeoverData(
        val takeoverReason: String,
        val takeoverAmount: BigDecimal ?= null,
    )

    class CustomerDeclaration(
        val productStream: String,
        val productWeight: BigDecimal ?= null
    )

    class ProductFeeData(
        val productFeeCode: ProductCode,
        val productFeeQuantity: BigDecimal,
        val productFeeMeasuringUnit: String,
        val productFeeRate: BigDecimal,
        val productFeeAmount: BigDecimal
    )

    class ProductFeeSummary(
        val productFeeOperation: String,
        val productFeeData: List<ProductFeeData>,
        val productChargeSum: BigDecimal,
        val paymentEvidenceDocumentData: PaymentEvidenceDocumentData? = null
    )

    class PaymentEvidenceDocumentData(
        val evidenceDocumentNo: String,
        val evidenceDocumentDate: LocalDate,
        val obligatedName: String,
        val obligatedAddress: Address,
        val obligatedTaxNumber: TaxNumber
    )

    class InvoiceHead(
        val supplierInfo: SupplierInfo,
        val customerInfo: CustomerInfo? = null,
        val fiscalRepresentativeInfo: FiscalRepresentativeInfo? = null,
        val invoiceDetail: InvoiceDetail
    )

    class FiscalRepresentativeInfo(
        val fiscalRepresentativeTaxNumber: TaxNumber,
        val fiscalRepresentativeName: String,
        val fiscalRepresentativeAddress: Address,
        val fiscalRepresentativeBankAccountNumber: String? = null
    )

    class InvoiceSummary(
        val summaryNormal: SummaryNormal? = null,
        val summarySimplified: List<SummarySimplified> ?= null,
        val summaryGrossData: SummaryGrossData? = null,
    )

    class SummaryNormal(
        @field:JacksonXmlProperty(localName = "summaryByVatRate")
        val summariesByVatRate: List<SummaryByVatRate>,
        val invoiceNetAmount: BigDecimal,
        val invoiceNetAmountHUF: BigDecimal,
        val invoiceVatAmount: BigDecimal,
        val invoiceVatAmountHUF: BigDecimal
    )

    class SummaryByVatRate(
        val vatRate: VatRate,
        val vatRateNetData: VatRateNetData,
        val vatRateVatData: VatRateVatData,
        val vatRateGrossData: VatRateGrossData? = null,
    )

    class VatRateNetData(
        val vatRateNetAmount: BigDecimal,
        val vatRateNetAmountHUF: BigDecimal,
    )

    class VatRateVatData(
        val vatRateVatAmount: BigDecimal,
        val vatRateVatAmountHUF: BigDecimal,
    )

    class VatRateGrossData(
        val vatRateGrossAmount: BigDecimal,
        val vatRateGrossAmountHUF: BigDecimal
    )

    class SummarySimplified(
        val vatRate: VatRate,
        val vatContentGrossAmount: BigDecimal,
        val vatContentGrossAmountHUF: BigDecimal,
    )

    class SummaryGrossData(
        val invoiceGrossAmount: BigDecimal? = null,
        val invoiceGrossAmountHUF: BigDecimal? = null,
    )

    class SupplierInfo(
        val supplierTaxNumber: TaxNumber,
        val groupMemberTaxNumber: TaxNumber? = null,
        val communityVatNumber: String? = null,
        val supplierName: String,
        val supplierAddress: Address,
        val supplierBankAccountNumber: String? = null,
        val individualExemption: Boolean? = null,
        val exciseLicenceNum: String? = null,
    )

    class CustomerInfo(
        val customerVatStatus: String,
        val customerVatData: CustomerVatData? = null,
        val customerName: String? = null,
        val customerAddress: Address? = null,
        val customerBankAccountNumber: String? = null
    )

    class CustomerVatData(
        val customerTaxNumber: CustomerTaxNumber? = null,
        val communityVatNumber: String? = null,
        val thirdStateTaxId: String? = null,
    )

    class CustomerTaxNumber(
        @field:JacksonXmlProperty(namespace = "base")
        val taxpayerId: String,

        @field:JacksonXmlProperty(namespace = "base")
        val vatCode: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val countyCode: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val groupMemberTaxNumber: TaxNumber? = null
    )

    class Address(
        @field:JacksonXmlProperty(namespace = "base")
        val simpleAddress: SimpleAddress? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val detailedAddress: DetailedAddress? = null,
    )

    class SimpleAddress(
        @field:JacksonXmlProperty(namespace = "base")
        val countryCode: String,

        @field:JacksonXmlProperty(namespace = "base")
        val region: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val postalCode: String,

        @field:JacksonXmlProperty(namespace = "base")
        val city: String,

        @field:JacksonXmlProperty(namespace = "base")
        val additionalAddressDetail: String
    )

    class DetailedAddress(
        @field:JacksonXmlProperty(namespace = "base")
        val countryCode: String,

        @field:JacksonXmlProperty(namespace = "base")
        val region: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val postalCode: String,

        @field:JacksonXmlProperty(namespace = "base")
        val city: String,

        @field:JacksonXmlProperty(namespace = "base")
        val streetName: String,

        @field:JacksonXmlProperty(namespace = "base")
        val publicPlaceCategory: String,

        @field:JacksonXmlProperty(namespace = "base")
        val number: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val building: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val staircase: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val floor: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val door: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val lotNumber: String? = null,
    )

    class TaxNumber(
        @field:JacksonXmlProperty(namespace = "base")
        val taxpayerId: String,

        @field:JacksonXmlProperty(namespace = "base")
        val vatCode: String? = null,

        @field:JacksonXmlProperty(namespace = "base")
        val countyCode: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TaxNumber
            if (taxpayerId != other.taxpayerId) return false
            return true
        }

        override fun hashCode(): Int {
            return taxpayerId.hashCode()
        }
    }

    class InvoiceDetail(
        val invoiceCategory: String,
        val invoiceDeliveryDate: LocalDate,
        val invoiceDeliveryPeriodStart: LocalDate? = null,
        val invoiceDeliveryPeriodEnd: LocalDate? = null,
        val invoiceAccountingDeliveryDate: LocalDate? = null,
        val periodicalSettlement: Boolean? = null,
        val smallBusinessIndicator: Boolean? = null,
        val currencyCode: String,
        val exchangeRate: BigDecimal,
        val utilitySettlementIndicator: Boolean? = null,
        val selfBillingIndicator: Boolean? = null,
        val paymentMethod: String? = null,
        val paymentDate: LocalDate? = null,
        val cashAccountingIndicator: Boolean? = null,
        val invoiceAppearance: String,
        val conventionalInvoiceInfo: ConventionalInvoiceInfo? = null,
        val additionalInvoiceData: AdditionalData? = null
    )

    class ConventionalInvoiceInfo(
        @field:JacksonXmlProperty(localName = "orderNumber")
        val orderNumbers: List<String>? = null,

        @field:JacksonXmlProperty(localName = "deliveryNote")
        val deliveryNotes: List<String>? = null,

        @field:JacksonXmlProperty(localName = "shippingDate")
        val shippingDates: List<String>? = null,

        @field:JacksonXmlProperty(localName = "contractNumber")
        val contractNumbers: List<String>? = null,

        @field:JacksonXmlProperty(localName = "supplierCompanyCode")
        val supplierCompanyCodes: List<String>? = null,

        @field:JacksonXmlProperty(localName = "customerCompanyCode")
        val customerCompanyCodes: List<String>? = null,

        @field:JacksonXmlProperty(localName = "dealerCode")
        val dealerCodes: List<String>? = null,

        @field:JacksonXmlProperty(localName = "costCenter")
        val costCenters: List<String>? = null,

        @field:JacksonXmlProperty(localName = "projectNumber")
        val projectNumbers: List<String>? = null,

        @field:JacksonXmlProperty(localName = "generalLedgerAccountNumber")
        val generalLedgerAccountNumbers: List<String>? = null,

        @field:JsonProperty("glnNumbersSupplier")
        val glnNumbersSupplier: List<String>? = null,

        @field:JsonProperty("glnNumbersCustomer")
        val glnNumbersCustomer: List<String>? = null,

        @field:JacksonXmlProperty(localName = "materialNumber")
        val materialNumbers: List<String>? = null,

        @field:JacksonXmlProperty(localName = "itemNumber")
        val itemNumbers: List<String>? = null,

        @field:JacksonXmlProperty(localName = "ekaerId")
        val ekaerIds: List<String>? = null,
    )

    class AdditionalData(
        val dataName: String,
        val dataDescription: String,
        val dataValue: String
    )
}