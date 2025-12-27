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
 * Uses original line structure to preserve column positions
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
        
        // Find table boundaries
        var startIndex = -1
        var endIndex = lines.size
        
        for (i in lines.indices) {
            if (lines[i].contains("Date") && 
                lines[i].contains("Narration") && 
                lines[i].contains("Withdrawal Amt.")) {
                startIndex = i + 1
                Log.d(TAG, "Found transaction table at line $i")
            }
            
            if (lines[i].contains("STATEMENT SUMMARY", ignoreCase = true) ||
                (lines[i].contains("Opening Balance", ignoreCase = true) && 
                 lines[i].contains("Dr Count", ignoreCase = true))) {
                endIndex = i
                Log.d(TAG, "Found statement summary at line $i")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table header")
            return transactions
        }
        
        var i = startIndex
        while (i < endIndex) {
            val line = lines[i]
            
            // Check if line starts with a date
            if (line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                try {
                    // This is a transaction line - parse it directly
                    val transaction = parseTransactionLine(line, lines, i, endIndex)
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
    
    private fun parseTransactionLine(line: String, allLines: List<String>, currentIndex: Int, endIndex: Int): PdfTransaction? {
        try {
            Log.d(TAG, "=== Parsing Line ===")
            Log.d(TAG, "Line: '$line'")
            
            // Extract date from start
            val dateMatch = Regex("""^(\d{2}[/-]\d{2}[/-]\d{2,4})""").find(line)
            if (dateMatch == null) {
                Log.w(TAG, "No date found")
                return null
            }
            
            val dateStr = dateMatch.groupValues[1]
            val afterDate = line.substring(dateMatch.range.last + 1)
            
            Log.d(TAG, "Date: $dateStr")
            Log.d(TAG, "After date: '$afterDate'")
            
            // Collect description from current and next lines until we find the reference number
            var description = ""
            var refNumber = ""
            var amountsLine = ""
            
            var j = currentIndex
            while (j < endIndex && j < currentIndex + 10) {
                val checkLine = if (j == currentIndex) afterDate else allLines[j]
                
                // Look for reference number (15-16 digits)
                val refMatch = Regex("""\b(\d{15,})\b""").find(checkLine)
                if (refMatch != null) {
                    refNumber = refMatch.groupValues[1]
                    
                    // Description is everything before ref number
                    val beforeRef = checkLine.substring(0, refMatch.range.first).trim()
                    if (beforeRef.isNotEmpty()) {
                        description += " " + beforeRef
                    }
                    
                    // Amounts are after ref number
                    amountsLine = checkLine.substring(refMatch.range.last + 1).trim()
                    
                    Log.d(TAG, "Found ref: $refNumber")
                    Log.d(TAG, "Amounts line: '$amountsLine'")
                    break
                } else {
                    // No ref found yet, add to description
                    description += " " + checkLine.trim()
                }
                
                j++
                
                // Stop if we hit another transaction date
                if (j < endIndex && allLines[j].matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                    break
                }
            }
            
            description = description.trim()
            Log.d(TAG, "Description: '$description'")
            
            // Parse amounts from amountsLine
            // Format: [value_date] [withdrawal] [deposit] [balance]
            
            // Remove value date first (it's duplicate)
            val withoutValueDate = amountsLine.replaceFirst(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), "").trim()
            
            Log.d(TAG, "Without value date: '$withoutValueDate'")
            
            // Now we need to parse: [withdrawal_col] [deposit_col] [balance_col]
            // Key insight: Use the last number as balance, then work backwards
            
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val allAmounts = amountPattern.findAll(withoutValueDate).toList()
            
            Log.d(TAG, "Found ${allAmounts.size} amounts")
            
            if (allAmounts.isEmpty()) {
                Log.w(TAG, "No amounts found")
                return null
            }
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            var balance: Double
            
            // The LAST amount is ALWAYS the balance
            balance = allAmounts.last().value.replace(",", "").toDouble()
            
            Log.d(TAG, "Balance (last): $balance")
            
            // If there are more amounts, we need to identify which are withdrawal/deposit
            when (allAmounts.size) {
                1 -> {
                    // Only balance, no transaction
                    Log.d(TAG, "Only balance, no transaction amount")
                }
                2 -> {
                    // One transaction amount + balance
                    // Need to determine if it's withdrawal or deposit
                    val txAmount = allAmounts[0].value.replace(",", "").toDouble()
                    
                    // Strategy: Look at the original line to see where this amount appears
                    // If it's close to the start (left side), it's withdrawal
                    // If it's close to the end (right side before balance), it's deposit
                    
                    val txPosition = allAmounts[0].range.first
                    val balancePosition = allAmounts[1].range.first
                    val totalLength = withoutValueDate.length
                    
                    // Calculate relative position (0.0 to 1.0)
                    val relativePos = txPosition.toFloat() / totalLength
                    
                    Log.d(TAG, "Transaction amount: $txAmount at position $txPosition/$totalLength (${relativePos * 100}%)")
                    
                    // If amount is in first 40% of line, it's withdrawal
                    // If in last 60%, it's deposit
                    if (relativePos < 0.4) {
                        withdrawal = txAmount
                        Log.d(TAG, "→ WITHDRAWAL (left side)")
                    } else {
                        deposit = txAmount
                        Log.d(TAG, "→ DEPOSIT (right side)")
                    }
                }
                3 -> {
                    // Both withdrawal and deposit present
                    withdrawal = allAmounts[0].value.replace(",", "").toDouble()
                    deposit = allAmounts[1].value.replace(",", "").toDouble()
                    Log.d(TAG, "Withdrawal: $withdrawal, Deposit: $deposit")
                }
            }
            
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "FINAL: Withdrawal=$withdrawal, Deposit=$deposit, Balance=$balance")
            Log.d(TAG, "==================")
            
            return PdfTransaction(
                date = dateStr,
                timestamp = timestamp,
                description = description,
                debit = withdrawal,
                credit = deposit,
                balance = balance
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction line", e)
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
