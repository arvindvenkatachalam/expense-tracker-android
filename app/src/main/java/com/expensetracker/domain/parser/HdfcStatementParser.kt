package com.expensetracker.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.expensetracker.data.model.PdfTransaction
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Parser for HDFC Bank statement PDFs
 * Handles the specific format used by HDFC Bank
 */
class HdfcStatementParser @Inject constructor() : PdfParser {
    
    companion object {
        private const val TAG = "HdfcStatementParser"
        
        // Date formats used by HDFC
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        )
        
        // Regex patterns for transaction parsing
        private val TRANSACTION_PATTERN = Regex(
            """(\d{2}[/-]\d{2}[/-]\d{2,4})\s+(.+?)\s+([\d,]+\.\d{2})?\s*([\d,]+\.\d{2})?\s*([\d,]+\.\d{2})""",
            RegexOption.MULTILINE
        )
        
        private val DATE_PATTERN = Regex("""(\d{2}[/-]\d{2}[/-]\d{2,4})""")
        private val AMOUNT_PATTERN = Regex("""([\d,]+\.\d{2})""")
    }
    
    override suspend fun parsePdf(context: Context, uri: Uri): List<PdfTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting PDF parsing for URI: $uri")
            
            // Initialize PDFBox
            PDFBoxResourceLoader.init(context)
            
            // Open PDF document
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw PdfParsingException("Cannot open PDF file")
            
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            
            // Extract text from all pages
            val text = stripper.getText(document)
            document.close()
            inputStream.close()
            
            Log.d(TAG, "Extracted text length: ${text.length}")
            Log.d(TAG, "First 500 chars: ${text.take(500)}")
            
            // Parse transactions from text
            val transactions = parseTransactions(text)
            
            Log.d(TAG, "Parsed ${transactions.size} transactions")
            
            transactions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF", e)
            throw PdfParsingException("Failed to parse PDF: ${e.message}", e)
        }
    }
    
    private fun parseTransactions(text: String): List<PdfTransaction> {
        val transactions = mutableListOf<PdfTransaction>()
        
        // Find all transaction matches
        val matches = TRANSACTION_PATTERN.findAll(text)
        
        for (match in matches) {
            try {
                val groups = match.groupValues
                
                // Extract fields
                val dateStr = groups[1]
                val description = groups[2].trim()
                val debitStr = groups[3].takeIf { it.isNotBlank() }
                val creditStr = groups[4].takeIf { it.isNotBlank() }
                val balanceStr = groups[5]
                
                // Parse date
                val timestamp = parseDate(dateStr)
                
                // Parse amounts
                val debit = debitStr?.let { parseAmount(it) }
                val credit = creditStr?.let { parseAmount(it) }
                val balance = parseAmount(balanceStr)
                
                // Create transaction
                val transaction = PdfTransaction(
                    date = dateStr,
                    timestamp = timestamp,
                    description = description,
                    debit = debit,
                    credit = credit,
                    balance = balance
                )
                
                transactions.add(transaction)
                
                Log.d(TAG, "Parsed transaction: $dateStr - $description - Debit: $debit, Credit: $credit")
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse transaction line: ${match.value}", e)
                // Continue with next transaction
            }
        }
        
        return transactions
    }
    
    private fun parseDate(dateStr: String): Long {
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(dateStr)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // Fallback to current time if parsing fails
        Log.w(TAG, "Could not parse date: $dateStr, using current time")
        return System.currentTimeMillis()
    }
    
    private fun parseAmount(amountStr: String): Double {
        // Remove commas and parse
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
