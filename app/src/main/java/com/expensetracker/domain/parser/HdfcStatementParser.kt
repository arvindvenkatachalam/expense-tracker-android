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
 * Uses spatial analysis of amount positions
 */
class HdfcStatementParser @Inject constructor() : PdfParser {
    
    companion object {
        private const val TAG = "HdfcStatementParser"
        
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.YEAR, 2000)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                set2DigitYearStart(calendar.time)
            },
            SimpleDateFormat("dd-MM-yy", Locale.getDefault()).apply {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.YEAR, 2000)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                set2DigitYearStart(calendar.time)
            }
        )
    }
    
    override suspend fun parsePdf(context: Context, uri: Uri, password: String?): List<PdfTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting PDF parsing")
            
            PDFBoxResourceLoader.init(context)
            
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw PdfParsingException("Cannot open PDF file")
            
            // First, try to load the PDF to check if it's encrypted
            val document = try {
                PDDocument.load(inputStream)
            } catch (e: Exception) {
                inputStream.close()
                // If loading fails and we don't have a password, it might be encrypted
                if (password == null && e.message?.contains("encrypted", ignoreCase = true) == true) {
                    throw PdfPasswordRequiredException("This PDF is password protected. Please enter your Customer ID.")
                }
                throw PdfParsingException("Cannot open PDF file: ${e.message}", e)
            }
            
            // Check if PDF is encrypted
            if (document.isEncrypted) {
                Log.d(TAG, "PDF is encrypted")
                document.close()
                inputStream.close()
                
                if (password == null) {
                    throw PdfPasswordRequiredException("This PDF is password protected. Please enter your Customer ID.")
                }
                
                // Reload with password
                val inputStream2 = context.contentResolver.openInputStream(uri)
                    ?: throw PdfParsingException("Cannot open PDF file")
                
                val decryptedDocument = try {
                    Log.d(TAG, "Attempting to load encrypted PDF with password")
                    PDDocument.load(inputStream2, password)
                } catch (e: Exception) {
                    inputStream2.close()
                    Log.e(TAG, "Failed to decrypt PDF: ${e.message}", e)
                    throw PdfInvalidPasswordException("Incorrect password. Please check your Customer ID and try again.")
                }
                
                // Verify decryption was successful
                if (decryptedDocument.isEncrypted) {
                    decryptedDocument.close()
                    inputStream2.close()
                    throw PdfInvalidPasswordException("Incorrect password. Please check your Customer ID and try again.")
                }
                
                Log.d(TAG, "PDF decrypted successfully")
                
                // Process the decrypted document
                val stripper = PDFTextStripper()
                val text = stripper.getText(decryptedDocument)
                decryptedDocument.close()
                inputStream2.close()
                
                Log.d(TAG, "Extracted text length: ${text.length}")
                
                val transactions = parseTransactions(text)
                
                Log.d(TAG, "Parsed ${transactions.size} transactions")
                Log.d(TAG, "Total debits: ${transactions.mapNotNull { it.debit }.sum()}")
                Log.d(TAG, "Total credits: ${transactions.mapNotNull { it.credit }.sum()}")
                
                return@withContext transactions
            }
            
            Log.d(TAG, "PDF is not encrypted, processing normally")
            
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            inputStream.close()
            
            Log.d(TAG, "Extracted text length: ${text.length}")
            
            val transactions = parseTransactions(text)
            
            Log.d(TAG, "Parsed ${transactions.size} transactions")
            Log.d(TAG, "Total debits: ${transactions.mapNotNull { it.debit }.sum()}")
            Log.d(TAG, "Total credits: ${transactions.mapNotNull { it.credit }.sum()}")
            
            transactions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF", e)
            // Re-throw password-related exceptions
            if (e is PdfPasswordRequiredException || e is PdfInvalidPasswordException) {
                throw e
            }
            throw PdfParsingException("Failed to parse PDF: ${e.message}", e)
        }
    }
    
    private fun parseTransactions(text: String): List<PdfTransaction> {
        val transactions = mutableListOf<PdfTransaction>()
        val lines = text.lines()
        
        var startIndex = -1
        var endIndex = lines.size
        
        for (i in lines.indices) {
            val line = lines[i]
            
            if (line.contains("Date") && line.contains("Narration") && 
                line.contains("Withdrawal Amt.") && line.contains("Deposit Amt.")) {
                startIndex = i + 1
                Log.d(TAG, "Found header at line $i: $line")
            }
            
            if (line.contains("STATEMENT SUMMARY", ignoreCase = true)) {
                endIndex = i
                Log.d(TAG, "Found summary at line $i")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table")
            return transactions
        }
        
        var i = startIndex
        while (i < endIndex) {
            val line = lines[i]
            
            if (line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                try {
                    val transaction = parseTransaction(lines, i, endIndex)
                    if (transaction != null) {
                        transactions.add(transaction)
                        
                        if (transaction.debit != null) {
                            Log.d(TAG, "✓ DEBIT: ${transaction.date} - ${transaction.description.take(50)} - ₹${transaction.debit}")
                        } else if (transaction.credit != null) {
                            Log.d(TAG, "✓ CREDIT: ${transaction.date} - ${transaction.description.take(50)} - ₹${transaction.credit}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse line $i", e)
                }
            }
            
            i++
        }
        
        return transactions
    }
    
    private fun parseTransaction(
        allLines: List<String>,
        currentIndex: Int,
        endIndex: Int
    ): PdfTransaction? {
        try {
            val firstLine = allLines[currentIndex]
            
            Log.d(TAG, "\n=== Line $currentIndex ===")
            Log.d(TAG, "First line: $firstLine")
            
            // Extract date
            val dateMatch = Regex("""^(\d{2}[/-]\d{2}[/-]\d{2,4})""").find(firstLine)
                ?: return null
            val dateStr = dateMatch.groupValues[1]
            
            // Collect ALL lines for this transaction
            val transactionLines = mutableListOf<String>()
            var j = currentIndex
            
            while (j < endIndex && j < currentIndex + 10) {
                val line = allLines[j]
                
                // Stop at next transaction
                if (j > currentIndex && line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                    break
                }
                if (line.contains("STATEMENT SUMMARY", ignoreCase = true)) break
                if (line.contains("Page No")) break
                
                transactionLines.add(line)
                j++
            }
            
            Log.d(TAG, "Collected ${transactionLines.size} lines")
            
            // Combine all lines
            val fullText = transactionLines.joinToString(" ").replace(Regex("""\s+"""), " ")
            Log.d(TAG, "Full text: $fullText")
            
            // Find reference number (16 digits starting with 0000)
            val refPattern = Regex("""\b(0000\d{12})\b""")
            val refMatch = refPattern.find(fullText)
            
            if (refMatch == null) {
                Log.w(TAG, "No reference number found")
                return null
            }
            
            val refNumber = refMatch.value
            Log.d(TAG, "Reference: $refNumber at position ${refMatch.range.first}")
            
            // Description is everything BEFORE the reference number
            val description = fullText.substring(0, refMatch.range.first)
                .replace(Regex("""\d{2}[/-]\d{2}[/-]\d{2,4}"""), "")  // Remove dates
                .replace(Regex("""\s+"""), " ")
                .trim()
            
            // Amounts section is AFTER the reference number
            val afterRef = fullText.substring(refMatch.range.last + 1).trim()
            
            Log.d(TAG, "Description: $description")
            Log.d(TAG, "After ref: '$afterRef'")
            
            // Remove the value date (first date after ref)
            val withoutValueDate = afterRef.replaceFirst(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), "").trim()
            
            Log.d(TAG, "After removing value date: '$withoutValueDate'")
            
            // Extract all amounts
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val allAmounts = amountPattern.findAll(withoutValueDate)
                .map { it.value.replace(",", "").toDouble() }
                .toList()
            
            Log.d(TAG, "Found amounts: $allAmounts")
            
            if (allAmounts.isEmpty()) {
                Log.w(TAG, "No amounts found")
                return null
            }
            
            // CRITICAL: Last amount is ALWAYS the closing balance
            val balance = allAmounts.last()
            Log.d(TAG, "Balance (last): $balance")
            
            // Process transaction amounts (all except last)
            val txAmounts = allAmounts.dropLast(1)
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            
            when (txAmounts.size) {
                0 -> {
                    Log.d(TAG, "No transaction amount (balance only)")
                }
                1 -> {
                    // Single transaction amount - need to determine type
                    val amount = txAmounts[0]
                    
                    // Get the positions in the original "withoutValueDate" string
                    val matches = amountPattern.findAll(withoutValueDate).toList()
                    if (matches.size >= 2) {
                        val txMatch = matches[0]
                        val balanceMatch = matches[1]
                        
                        val distance = balanceMatch.range.first - txMatch.range.last
                        
                        Log.d(TAG, "Transaction: $amount, Balance: $balance")
                        Log.d(TAG, "Distance between amounts: $distance chars")
                        
                        // If there's significant space (30+ chars), it's withdrawal
                        // If close together (<30 chars), it's deposit
                        if (distance >= 25) {
                            withdrawal = amount
                            Log.d(TAG, "→ WITHDRAWAL (large gap: $distance)")
                        } else {
                            deposit = amount
                            Log.d(TAG, "→ DEPOSIT (small gap: $distance)")
                        }
                    } else {
                        // Fallback
                        withdrawal = amount
                        Log.d(TAG, "→ WITHDRAWAL (fallback)")
                    }
                }
                2 -> {
                    // Both withdrawal and deposit
                    withdrawal = txAmounts[0]
                    deposit = txAmounts[1]
                    Log.d(TAG, "Withdrawal: $withdrawal, Deposit: $deposit")
                }
                else -> {
                    // Multiple amounts, take last 2 before balance
                    if (txAmounts.size >= 2) {
                        withdrawal = txAmounts[txAmounts.size - 2]
                        deposit = txAmounts[txAmounts.size - 1]
                    }
                }
            }
            
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "FINAL: Date=$dateStr, Withdrawal=$withdrawal, Deposit=$deposit, Balance=$balance")
            
            return PdfTransaction(
                date = dateStr,
                timestamp = timestamp,
                description = description,
                debit = withdrawal,
                credit = deposit,
                balance = balance
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
                continue
            }
        }
        
        return System.currentTimeMillis()
    }
}