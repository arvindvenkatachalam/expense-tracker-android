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
        var endIndex = lines.size
        
        // Store previous balance to help determine transaction type
        var previousBalance: Double? = null
        
        for (i in lines.indices) {
            if (lines[i].contains("Date") && 
                lines[i].contains("Narration") && 
                lines[i].contains("Withdrawal Amt.")) {
                startIndex = i + 1
                Log.d(TAG, "Found transaction table at line $i")
            }
            
            // Find where statement summary starts - stop parsing here
            if (lines[i].contains("STATEMENT SUMMARY", ignoreCase = true) ||
                lines[i].contains("Opening Balance", ignoreCase = true) && 
                lines[i].contains("Dr Count", ignoreCase = true)) {
                endIndex = i
                Log.d(TAG, "Found statement summary at line $i, stopping transaction parsing")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table header")
            startIndex = 0
        }
        
        var i = startIndex
        while (i < endIndex) {
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
                    // Collect subsequent lines until we hit another date, summary, or end
                    while (j < endIndex) {
                        val nextLine = lines[j].trim()
                        
                        // Stop if we hit another transaction (starts with date)
                        if (nextLine.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                            break
                        }
                        
                        // Stop if we hit HDFC BANK LIMITED footer
                        if (nextLine.contains("HDFC BANK LIMITED")) {
                            break
                        }
                        
                        // Stop if we hit statement summary
                        if (nextLine.contains("STATEMENT SUMMARY", ignoreCase = true)) {
                            break
                        }
                        
                        if (nextLine.isNotEmpty()) {
                            transactionLines.add(nextLine)
                        }
                        j++
                    }
                    
                    // Parse the collected transaction block
                    val transaction = parseTransactionBlock(dateStr, transactionLines, previousBalance)
                    if (transaction != null) {
                        transactions.add(transaction)
                        
                        // Update previous balance for next transaction
                        previousBalance = transaction.balance
                        
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
    
    private fun parseTransactionBlock(dateStr: String, lines: List<String>, previousBalance: Double?): PdfTransaction? {
        try {
            // Combine all lines into one block
            val fullText = lines.joinToString(" ")
            
            Log.d(TAG, "=== Parsing Transaction Block ===")
            Log.d(TAG, "Full text: $fullText")
            
            // Extract description (everything between first date and reference number)
            // Reference number is typically 15-16 digits
            val refNumberPattern = Regex("""\d{15,}""")
            val refMatch = refNumberPattern.find(fullText)
            
            var description = ""
            var amountsSection = ""
            
            if (refMatch != null) {
                // Description is before the reference number
                description = fullText.substring(0, refMatch.range.first).trim()
                // Remove the date from description
                description = description.replace(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}\s*"""), "").trim()
                
                // Text after reference number contains: value date, withdrawal, deposit, balance
                amountsSection = fullText.substring(refMatch.range.last + 1).trim()
            } else {
                // No reference number found
                description = lines.drop(1).joinToString(" ").trim()
                amountsSection = lines.lastOrNull() ?: ""
            }
            
            Log.d(TAG, "Description: $description")
            Log.d(TAG, "Amounts section: '$amountsSection'")
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            var balance: Double? = null
            
            // Remove the value date first
            val withoutValueDate = amountsSection.replaceFirst(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), "").trim()
            
            Log.d(TAG, "After removing value date: '$withoutValueDate'")
            
            // Extract all decimal amounts (format: xxx.xx or x,xxx.xx)
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val amountMatches = amountPattern.findAll(withoutValueDate).toList()
            
            Log.d(TAG, "Found ${amountMatches.size} amount matches")
            
            // Get amounts with their positions
            data class AmountWithPosition(val value: Double, val position: Int, val text: String)
            val amountsWithPos = amountMatches.map { match ->
                val amountStr = match.groupValues[1]
                val value = amountStr.replace(",", "").toDouble()
                AmountWithPosition(value, match.range.first, amountStr)
            }
            
            amountsWithPos.forEachIndexed { index, amt ->
                Log.d(TAG, "Amount[$index]: ${amt.text} (${amt.value}) at position ${amt.position}")
            }
            
            when (amountsWithPos.size) {
                0 -> {
                    Log.w(TAG, "No amounts found in transaction")
                    balance = 0.0
                }
                1 -> {
                    // Only balance
                    balance = amountsWithPos[0].value
                    Log.d(TAG, "Case 1: Only balance = $balance")
                }
                2 -> {
                    // One transaction + balance
                    // Last is always balance
                    balance = amountsWithPos[1].value
                    
                    // Calculate the gap between amounts
                    val gap = amountsWithPos[1].position - (amountsWithPos[0].position + amountsWithPos[0].text.length)
                    
                    Log.d(TAG, "Gap between amounts: $gap characters")
                    
                    // If gap is large (>10 spaces), first amount is withdrawal, second is balance
                    // If gap is medium (5-10 spaces), first amount is deposit, second is balance
                    if (gap > 10) {
                        withdrawal = amountsWithPos[0].value
                        Log.d(TAG, "Case 2a: Large gap -> Withdrawal = $withdrawal, Balance = $balance")
                    } else {
                        deposit = amountsWithPos[0].value
                        Log.d(TAG, "Case 2b: Small gap -> Deposit = $deposit, Balance = $balance")
                    }
                }
                3 -> {
                    // Withdrawal, deposit, balance
                    withdrawal = amountsWithPos[0].value
                    deposit = amountsWithPos[1].value
                    balance = amountsWithPos[2].value
                    Log.d(TAG, "Case 3: Withdrawal = $withdrawal, Deposit = $deposit, Balance = $balance")
                }
                else -> {
                    // More than 3 amounts - take last as balance
                    balance = amountsWithPos.last().value
                    if (amountsWithPos.size >= 3) {
                        withdrawal = amountsWithPos[amountsWithPos.size - 3].value
                        deposit = amountsWithPos[amountsWithPos.size - 2].value
                    }
                    Log.d(TAG, "Case 4: Multiple amounts - Withdrawal = $withdrawal, Deposit = $deposit, Balance = $balance")
                }
            }
            
            // Parse timestamp
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "FINAL RESULT -> Date: $dateStr, Withdrawal: $withdrawal, Deposit: $deposit, Balance: $balance")
            Log.d(TAG, "=================================")
            
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
