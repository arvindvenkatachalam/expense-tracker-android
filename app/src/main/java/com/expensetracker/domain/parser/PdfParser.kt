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
     * @param context Android context for accessing content resolver
     * @param uri URI of the PDF file
     * @param password Optional password for encrypted PDFs
     * @return List of extracted transactions
     * @throws PdfParsingException if parsing fails
     * @throws PdfPasswordRequiredException if PDF is encrypted and no password provided
     * @throws PdfInvalidPasswordException if provided password is incorrect
     */
    suspend fun parsePdf(context: Context, uri: Uri, password: String? = null): List<PdfTransaction>
}

/**
 * Exception thrown when PDF parsing fails
 */
class PdfParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when PDF is encrypted and requires a password
 */
class PdfPasswordRequiredException(message: String = "Password required") : Exception(message)

/**
 * Exception thrown when the provided PDF password is incorrect
 */
class PdfInvalidPasswordException(message: String = "Invalid password") : Exception(message)

/**
 * Result of PDF parsing operation
 */
sealed class PdfParseResult {
    data class Success(val transactions: List<PdfTransaction>) : PdfParseResult()
    data class Error(val message: String, val exception: Throwable? = null) : PdfParseResult()
    data class Empty(val message: String = "No transactions found in PDF") : PdfParseResult()
}
