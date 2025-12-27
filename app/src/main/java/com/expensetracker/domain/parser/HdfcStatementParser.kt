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
 * Uses fixed column width approach for reliable parsing
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
        
        // Column boundaries (will be auto-detected from header)
        data class ColumnPositions(
            val withdrawalStart: Int = -1,
            val depositStart: Int = -1,
            val balanceStart: Int = -1
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
        
        // Find table header and detect column positions
        var startIndex = -1
        var endIndex = lines.size
        var columnPositions = ColumnPositions()
        
        for (i in lines.indices) {
            val line = lines[i]
            
            // Detect header line with column titles
            if (line.contains("Withdrawal Amt.") && line.contains("Deposit Amt.") && line.contains("Closing Balance")) {
                startIndex = i + 1
                
                // Detect column positions from header
                val withdrawalPos = line.indexOf("Withdrawal Amt.")
                val depositPos = line.indexOf("Deposit Amt.")
                val balancePos = line.indexOf("Closing Balance")
                
                columnPositions = ColumnPositions(
                    withdrawalStart = if (withdrawalPos >= 0) withdrawalPos else -1,
                    depositStart = if (depositPos >= 0) depositPos else -1,
                    balanceStart = if (balancePos >= 0) balancePos else -1
                )
                
                Log.d(TAG, "Found header at line $i")
                Log.d(TAG, "Column positions - Withdrawal: ${columnPositions.withdrawalStart}, Deposit: ${columnPositions.depositStart}, Balance: ${columnPositions.balanceStart}")
            }
            
            // Find statement summary
            if (line.contains("STATEMENT SUMMARY", ignoreCase = true) ||
                (line.contains("Opening Balance", ignoreCase = true) && 
                 line.contains("Dr Count", ignoreCase = true))) {
                endIndex = i
                Log.d(TAG, "Found statement summary at line $i")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table header")
            return transactions
        }
        
        // If column detection failed, use default positions
        if (columnPositions.balanceStart == -1) {
            Log.w(TAG, "Using default column positions")
            columnPositions = ColumnPositions(
                withdrawalStart = 60,
                depositStart = 85,
                balanceStart = 110
            )
        }
        
        var i = startIndex
        while (i < endIndex) {
            val line = lines[i]
            
            // Check if line starts with a date
            if (line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                try {
                    val transaction = parseTransactionWithColumns(line, lines, i, endIndex, columnPositions)
                    if (transaction != null) {
                        transactions.add(transaction)
                        
                        if (transaction.debit != null && transaction.debit > 0) {
                            Log.d(TAG, "✓ WITHDRAWAL: ${transaction.date} - ${transaction.description} - ₹${transaction.debit}")
                        } else if (transaction.credit != null && transaction.credit > 0) {
                            Log.d(TAG, "✓ DEPOSIT: ${transaction.date} - ${transaction.description} - ₹${transaction.credit}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse line $i: $line", e)
                }
            }
            
            i++
        }
        
        return transactions
    }
    
    private fun parseTransactionWithColumns(
        line: String, 
        allLines: List<String>, 
        currentIndex: Int, 
        endIndex: Int,
        columnPositions: ColumnPositions
    ): PdfTransaction? {
        try {
            Log.d(TAG, "=== Parsing Line $currentIndex ===")
            Log.d(TAG, "Line: '$line'")
            Log.d(TAG, "Line length: ${line.length}")
            
            // Extract date
            val dateMatch = Regex("""^(\d{2}[/-]\d{2}[/-]\d{2,4})""").find(line)
            if (dateMatch == null) {
                Log.w(TAG, "No date found")
                return null
            }
            val dateStr = dateMatch.groupValues[1]
            
            // Collect full transaction block
            val transactionLines = mutableListOf<String>()
            transactionLines.add(line)
            
            var j = currentIndex + 1
            var amountsLine = line  // The line containing amounts
            
            while (j < endIndex && j < currentIndex + 10) {
                val nextLine = allLines[j]
                
                // Stop if we hit another transaction
                if (nextLine.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                    break
                }
                
                // Stop if we hit summary
                if (nextLine.contains("STATEMENT SUMMARY", ignoreCase = true)) {
                    break
                }
                
                transactionLines.add(nextLine)
                
                // The line with amounts typically has a reference number
                // and ends with balance
                if (nextLine.contains(Regex("""\d{15,}"""))) {
                    // Check if this line or next has amounts
                    if (nextLine.contains(Regex("""[\d,]+\.\d{2}"""))) {
                        amountsLine = nextLine
                    } else if (j + 1 < endIndex) {
                        amountsLine = allLines[j + 1]
                        transactionLines.add(amountsLine)
                    }
                    break
                }
                
                j++
            }
            
            Log.d(TAG, "Amounts line: '$amountsLine'")
            Log.d(TAG, "Amounts line length: ${amountsLine.length}")
            
            // Extract description
            val fullText = transactionLines.joinToString(" ")
            val refMatch = Regex("""\b(\d{15,})\b""").find(fullText)
            val description = if (refMatch != null) {
                fullText.substring(0, refMatch.range.first)
                    .replace(Regex("""\d{2}[/-]\d{2}[/-]\d{2,4}"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            } else {
                fullText.replace(Regex("""\d{2}[/-]\d{2}[/-]\d{2,4}"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            }
            
            Log.d(TAG, "Description: '$description'")
            
            // Extract amounts using column positions
            var withdrawal: Double? = null
            var deposit: Double? = null
            var balance: Double? = null
            
            // Find all amounts in the amounts line
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val allMatches = amountPattern.findAll(amountsLine).toList()
            
            Log.d(TAG, "Found ${allMatches.size} amounts in line")
            
            // Map amounts to columns based on their position
            for (match in allMatches) {
                val amount = match.value.replace(",", "").toDouble()
                val position = match.range.first
                
                Log.d(TAG, "Amount: $amount at position $position")
                
                // Determine which column this amount belongs to
                when {
                    // Balance column (rightmost)
                    position >= columnPositions.balanceStart -> {
                        balance = amount
                        Log.d(TAG, "  → BALANCE (pos >= ${columnPositions.balanceStart})")
                    }
                    // Deposit column
                    position >= columnPositions.depositStart && columnPositions.depositStart > 0 -> {
                        deposit = amount
                        Log.d(TAG, "  → DEPOSIT (pos >= ${columnPositions.depositStart})")
                    }
                    // Withdrawal column
                    position >= columnPositions.withdrawalStart && columnPositions.withdrawalStart > 0 -> {
                        withdrawal = amount
                        Log.d(TAG, "  → WITHDRAWAL (pos >= ${columnPositions.withdrawalStart})")
                    }
                }
            }
            
            // Fallback: if balance is null, last amount is balance
            if (balance == null && allMatches.isNotEmpty()) {
                balance = allMatches.last().value.replace(",", "").toDouble()
                Log.d(TAG, "Fallback: Using last amount as balance: $balance")
            }
            
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "FINAL: Date=$dateStr, Withdrawal=$withdrawal, Deposit=$deposit, Balance=$balance")
            Log.d(TAG, "==================")
            
            return PdfTransaction(
                date = dateStr,
                timestamp = timestamp,
                description = description,
                debit = withdrawal,
                credit = deposit,
                balance = balance ?: 0.0
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction", e)
            return null
        }
    }
    
    private fun parseDate(dateStr: String): Long {
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(dateStr)
                if (date != null) {
                    var timestamp = date.time
                    
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val year = calendar.get(Calendar.YEAR)
                    
                    if (year < 1900) {
                        calendar.set(Calendar.YEAR, year + 2000)
                        timestamp = calendar.timeInMillis
                    }
                    
                    return timestamp
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        Log.w(TAG, "Could not parse date: $dateStr, using current time")
        return System.currentTimeMillis()
    }
}
