package com.expensetracker.domain.parser

import android.content.Context
import android.net.Uri
import com.expensetracker.data.model.PdfTransaction

/**
 * Interface for parsing bank statement PDFs
 */
interface PdfParser {
    /**
     * Parse a PDF file and extract transactions
     */
    suspend fun parsePdf(context: Context, uri: Uri, password: String? = null): List<PdfTransaction>
}

/**
 * Exception thrown when PDF parsing fails
 */
class PdfParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when PDF is password protected
 */
class PdfPasswordRequiredException(message: String) : Exception(message)

/**
 * Exception thrown when provided password is incorrect
 */
class PdfInvalidPasswordException(message: String) : Exception(message)

/**
 * Result of PDF parsing operation
 */
sealed class PdfParseResult {
    data class Success(val transactions: List<PdfTransaction>) : PdfParseResult()
    data class Error(val message: String, val exception: Throwable? = null) : PdfParseResult()
    data class Empty(val message: String = "No transactions found in PDF") : PdfParseResult()
}