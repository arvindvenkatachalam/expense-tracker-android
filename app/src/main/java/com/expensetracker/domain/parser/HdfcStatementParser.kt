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
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.YEAR, 2000)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                set2DigitYearStart(calendar.time)
            },
            SimpleDateFormat("dd-MM-yy", Locale.getDefault()).apply {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.YEAR, 2000)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                set2DigitYearStart(calendar.time)
            }
        )
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
            Log.d(TAG, "First 1000 chars: ${text.take(1000)}")
            
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
        val lines = text.lines()
        
        // Find where transactions start (after "Statement of account" header)
        var startIndex = -1
        for (i in lines.indices) {
            if (lines[i].contains("Date") && 
                lines[i].contains("Narration") && 
                lines[i].contains("Withdrawal Amt.")) {
                startIndex = i + 1
                Log.d(TAG, "Found transaction table at line $i")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table header")
            startIndex = 0
        }
        
        var i = startIndex
        while (i < lines.size) {
            val line = lines[i].trim()
            
            // Check if line starts with a date
            val dateMatch = Regex("""^(\d{2}[/-]\d{2}[/-]\d{2,4})""").find(line)
            
            if (dateMatch != null) {
                try {
                    val dateStr = dateMatch.groupValues[1]
                    Log.d(TAG, "Processing transaction starting at line $i: $line")
                    
                    // Collect all lines that belong to this transaction
                    val transactionLines = mutableListOf<String>()
                    transactionLines.add(line)
                    
                    var j = i + 1
                    // Collect subsequent lines until we hit another date or end
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()
                        
                        // Stop if we hit another transaction (starts with date)
                        if (nextLine.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                            break
                        }
                        
                        // Stop if we hit HDFC BANK LIMITED footer
                        if (nextLine.contains("HDFC BANK LIMITED")) {
                            break
                        }
                        
                        if (nextLine.isNotEmpty()) {
                            transactionLines.add(nextLine)
                        }
                        j++
                    }
                    
                    // Parse the collected transaction block
                    val transaction = parseTransactionBlock(dateStr, transactionLines)
                    if (transaction != null) {
                        transactions.add(transaction)
                        
                        // Log withdrawal transactions
                        if (transaction.debit != null && transaction.debit > 0) {
                            Log.d(TAG, "✓ WITHDRAWAL: ${transaction.date} - ${transaction.description} - ₹${transaction.debit}")
                        } else if (transaction.credit != null && transaction.credit > 0) {
                            Log.d(TAG, "✓ DEPOSIT: ${transaction.date} - ${transaction.description} - ₹${transaction.credit}")
                        }
                    }
                    
                    // Move to next transaction
                    i = j
                    continue
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse transaction at line $i", e)
                }
            }
            
            i++
        }
        
        return transactions
    }
    
    private fun parseTransactionBlock(dateStr: String, lines: List<String>): PdfTransaction? {
        try {
            // Combine all lines into one block for easier parsing
            val fullText = lines.joinToString(" ")
            
            Log.d(TAG, "Parsing block: $fullText")
            
            // Extract description (everything between first date and reference number)
            // Reference number is typically 15-16 digits
            val refNumberPattern = Regex("""\d{15,}""")
            val refMatch = refNumberPattern.find(fullText)
            
            var description = ""
            var afterRefText = ""
            
            if (refMatch != null) {
                // Description is before the reference number
                description = fullText.substring(0, refMatch.range.first).trim()
                // Remove the date from description
                description = description.replace(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}\s*"""), "").trim()
                
                // Text after reference number contains: value date, withdrawal, deposit, balance
                afterRefText = fullText.substring(refMatch.range.last + 1).trim()
            } else {
                // No reference number found, try to extract description differently
                description = lines.drop(1).joinToString(" ").trim()
                afterRefText = lines.lastOrNull() ?: ""
            }
            
            Log.d(TAG, "Description: $description")
            Log.d(TAG, "After ref: $afterRefText")
            
            // Extract amounts from the end
            // Pattern: value_date withdrawal deposit balance
            // withdrawal and deposit are optional
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val amounts = amountPattern.findAll(afterRefText).map { 
                it.groupValues[1].replace(",", "").toDouble() 
            }.toList()
            
            Log.d(TAG, "Found amounts: $amounts")
            
            // The amounts appear in order: [withdrawal], [deposit], balance
            // We need to figure out which is which based on the pattern
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            var balance: Double? = null
            
            when (amounts.size) {
                1 -> {
                    // Only balance
                    balance = amounts[0]
                }
                2 -> {
                    // Could be: withdrawal + balance OR deposit + balance
                    // Check if description indicates deposit (UPI credits usually have "CREDIT" or incoming payments)
                    if (description.contains("CREDIT", ignoreCase = true) ||
                        description.contains("SALARY", ignoreCase = true) ||
                        description.contains("NEFT", ignoreCase = true) ||
                        description.contains("IMPS", ignoreCase = true)) {
                        deposit = amounts[0]
                        balance = amounts[1]
                    } else {
                        withdrawal = amounts[0]
                        balance = amounts[1]
                    }
                }
                3 -> {
                    // withdrawal, deposit, balance
                    withdrawal = amounts[0]
                    deposit = amounts[1]
                    balance = amounts[2]
                }
                else -> {
                    // Take last as balance
                    balance = amounts.lastOrNull()
                    // If more than 3, try to get withdrawal from reasonable position
                    if (amounts.size > 1) {
                        withdrawal = amounts[amounts.size - 3]
                    }
                }
            }
            
            // Parse timestamp
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "Final: Date=$dateStr, Withdrawal=$withdrawal, Deposit=$deposit, Balance=$balance")
            
            return PdfTransaction(
                date = dateStr,
                timestamp = timestamp,
                description = description,
                debit = withdrawal,
                credit = deposit,
                balance = balance ?: 0.0
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction block", e)
            return null
        }
    }
    
    private fun parseDate(dateStr: String): Long {
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(dateStr)
                if (date != null) {
                    var timestamp = date.time
                    
                    // Validate the year is reasonable
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val year = calendar.get(Calendar.YEAR)
                    
                    // If year is less than 1900, add 2000 to fix it
                    if (year < 1900) {
                        Log.w(TAG, "Year $year seems incorrect for date '$dateStr', adjusting to ${year + 2000}")
                        calendar.set(Calendar.YEAR, year + 2000)
                        timestamp = calendar.timeInMillis
                    }
                    
                    Log.d(TAG, "Parsed date '$dateStr' as: ${Date(timestamp)} (timestamp: $timestamp)")
                    return timestamp
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // Fallback to current time if parsing fails
        Log.w(TAG, "Could not parse date: $dateStr, using current time")
        return System.currentTimeMillis()
    }
}
