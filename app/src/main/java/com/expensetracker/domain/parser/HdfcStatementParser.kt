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
        while (i < endIndex) {  // Changed from lines.size to endIndex
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
                    while (j < endIndex) {  // Changed from lines.size to endIndex
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
            // Combine all lines into one block
            val fullText = lines.joinToString(" ")
            
            Log.d(TAG, "Parsing block: $fullText")
            
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
            Log.d(TAG, "Amounts section: $amountsSection")
            
            // Parse the amounts section properly
            // HDFC Format after ref: [value_date] [withdrawal_space] [deposit_space] [balance]
            // Where withdrawal_space and deposit_space can be empty or contain amount
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            var balance: Double? = null
            
            // Strategy: Split by multiple spaces to identify column boundaries
            // First, remove the value date
            val withoutValueDate = amountsSection.replaceFirst(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), "").trim()
            
            Log.d(TAG, "After removing value date: '$withoutValueDate'")
            
            // Now we have: [withdrawal_space] [deposit_space] [balance]
            // Split by 2+ spaces to separate columns
            val parts = withoutValueDate.split(Regex("""\s{2,}""")).filter { it.isNotBlank() }
            
            Log.d(TAG, "Split parts: $parts")
            
            // If we can split by spaces, use that to determine columns
            if (parts.size >= 1) {
                // Parse each part as a potential amount
                val amounts = parts.mapNotNull { part ->
                    val cleaned = part.trim().replace(",", "")
                    cleaned.toDoubleOrNull()
                }
                
                Log.d(TAG, "Parsed amounts from parts: $amounts")
                
                when (amounts.size) {
                    1 -> {
                        // Only balance present (no withdrawal or deposit)
                        balance = amounts[0]
                    }
                    2 -> {
                        // Two amounts: either (withdrawal, balance) OR (deposit, balance)
                        // The last one is always balance
                        balance = amounts[1]
                        
                        // Determine if first is withdrawal or deposit
                        // Check position in original text - if it appears early, likely withdrawal
                        val firstAmount = amounts[0]
                        val firstAmountStr = String.format("%.2f", firstAmount).replace(".00", "").replace(",", "")
                        val positionInText = withoutValueDate.indexOf(firstAmountStr)
                        
                        // If amount appears in first half of text, it's withdrawal column
                        // If in second half, it's deposit column
                        if (positionInText < withoutValueDate.length / 2) {
                            withdrawal = firstAmount
                        } else {
                            deposit = firstAmount
                        }
                    }
                    3 -> {
                        // Three amounts: withdrawal, deposit, balance
                        withdrawal = amounts[0]
                        deposit = amounts[1]
                        balance = amounts[2]
                    }
                    else -> {
                        balance = amounts.lastOrNull()
                    }
                }
            } else {
                // Fallback: extract all amounts and use last as balance
                val amountPattern = Regex("""([\d,]+\.\d{2})""")
                val allAmounts = amountPattern.findAll(withoutValueDate).map { 
                    it.groupValues[1].replace(",", "").toDouble() 
                }.toList()
                
                Log.d(TAG, "Fallback - all amounts: $allAmounts")
                
                when (allAmounts.size) {
                    1 -> balance = allAmounts[0]
                    2 -> {
                        balance = allAmounts[1]
                        withdrawal = allAmounts[0]
                    }
                    3 -> {
                        withdrawal = allAmounts[0]
                        deposit = allAmounts[1]
                        balance = allAmounts[2]
                    }
                    else -> balance = allAmounts.lastOrNull()
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
