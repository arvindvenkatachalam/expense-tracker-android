package com.expensetracker.service.parser

import android.util.Log
import java.util.regex.Pattern

class TransactionParser {
    
    companion object {
        private const val TAG = "TransactionParser"
        
        // Known bank sender patterns
        private val BANK_SENDERS = listOf(
            "HDFCBK", "HDFC", "ICICIB", "ICICI", "SBIINB", "SBI",
            "AXISBK", "AXIS", "KOTAKBNK", "KOTAK", "PNBSMS", "PNB",
            "BOIIND", "BOI", "CBSSBI", "CANBNK", "CANARA", "UNIONBK",
            "TMBSMS", "TMB",  // Tamilnad Mercantile Bank
            "IDFCFB", "IDFC",  // IDFC First Bank
            "YESBNK", "YES",   // Yes Bank
            "INDBNK", "INDIAN",  // Indian Bank
            "SCBANK", "SC",    // Standard Chartered
            "CITIBK", "CITI",  // Citibank
            "HSBCIN", "HSBC",  // HSBC
            "DEUTIN", "DEUTSCHE"  // Deutsche Bank
        )
        
        // Regex patterns for parsing
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:amount|amt)\\s*(?:of)?\\s*(?:Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE)
        )
        
        private val DEBIT_KEYWORDS = listOf(
            "debited", "withdrawn", "spent", "paid", "purchase", "debit", "used",
            "sent"  // UPI transactions
        )
        
        private val CREDIT_KEYWORDS = listOf(
            "credited", "deposited", "received", "credit", "refund"
        )
        
        private val MERCHANT_PATTERNS = listOf(
            Pattern.compile("(?:at|to|for|on)\\s+([A-Z][A-Z0-9\\s&-]+?)(?:\\s+on|\\.|,|\\s+avl|\\s+info)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:merchant|vendor)\\s+([A-Z][A-Z0-9\\s&-]+?)(?:\\.|,|\\s)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("To\\s+([A-Z][A-Z\\s]+?)(?:\\s+On|\\n|$)", Pattern.CASE_INSENSITIVE)  // UPI "To NAME" pattern
        )
        
        private val ACCOUNT_PATTERN = Pattern.compile("(?:A/c|account|card)\\s*(?:no\\.?)?\\s*(?:XX|\\*\\*|ending)?\\s*(\\d{4})", Pattern.CASE_INSENSITIVE)
        
        // Keywords that indicate this is NOT a transaction (statements, bills, alerts, promotions)
        private val EXCLUDE_KEYWORDS = listOf(
            // Statements and bills
            "statement", "total amount due", "min amount due", "minimum due",
            "payment due", "bill generated", "outstanding", "due date",
            // Balance alerts
            "available balance", "avl bal", "current balance", "balance is",
            // Rewards and limits
            "reward points", "cashpoints", "credit limit", "limit available",
            // Auto-debit and EMI
            "auto debit", "emi deducted", "emi due", "standing instruction",
            // Promotional and vouchers
            "voucher", "congrats", "congratulations", "offer", "cashback offer",
            "claim now", "redeem", "promo code", "discount code", "coupon",
            "t&c apply", "terms and conditions"
        )
    }
    
    fun isBankSms(sender: String): Boolean {
        val normalizedSender = sender.uppercase().replace("-", "").replace("_", "")
        return BANK_SENDERS.any { normalizedSender.contains(it) }
    }
    
    fun parseTransaction(smsBody: String, sender: String): ParsedTransaction? {
        try {
            Log.d(TAG, "Parsing SMS from $sender: $smsBody")
            
            val lowerBody = smsBody.lowercase()
            
            // Smart filtering: Only exclude if it's NOT a transaction
            // Check if SMS contains transaction keywords (debited, credited, paid, etc.)
            val hasTransactionKeyword = DEBIT_KEYWORDS.any { lowerBody.contains(it) } || 
                                       CREDIT_KEYWORDS.any { lowerBody.contains(it) }
            
            // Only apply exclusion filter if NO transaction keywords found
            // This prevents legitimate transactions with balance info from being excluded
            if (!hasTransactionKeyword && EXCLUDE_KEYWORDS.any { lowerBody.contains(it) }) {
                Log.d(TAG, "Skipping non-transaction SMS (statement/bill/alert/promo)")
                return null
            }
            
            // Extract amount
            val amount = extractAmount(smsBody) ?: run {
                Log.w(TAG, "Could not extract amount from SMS")
                return null
            }
            
            // Determine transaction type
            val transactionType = determineTransactionType(smsBody)
            
            // Extract merchant
            val merchant = extractMerchant(smsBody) ?: "Unknown Merchant"
            
            // Extract account number
            val accountLast4 = extractAccountNumber(smsBody)
            
            // Determine bank name
            val bankName = determineBankName(sender)
            
            val parsed = ParsedTransaction(
                amount = amount,
                merchant = merchant.trim(),
                transactionType = transactionType,
                timestamp = System.currentTimeMillis(),
                accountLast4 = accountLast4,
                bankName = bankName,
                rawSms = smsBody
            )
            
            Log.d(TAG, "Successfully parsed: $parsed")
            return parsed
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction", e)
            return null
        }
    }
    
    private fun extractAmount(smsBody: String): Double? {
        // First, try to find amounts that are NOT balance amounts
        // Balance patterns to avoid: "avl bal", "available balance", "current balance"
        val balancePattern = Pattern.compile("(?:avl|available|current)\\s*(?:bal|balance)\\s*(?:is)?\\s*(?:Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE)
        val balanceMatcher = balancePattern.matcher(smsBody)
        val balanceAmount = if (balanceMatcher.find()) {
            balanceMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        } else null
        
        // Extract all amounts from SMS
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(smsBody)
            while (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: continue
                val amount = amountStr.toDoubleOrNull() ?: continue
                
                // Skip if this amount matches the balance amount
                if (balanceAmount != null && Math.abs(amount - balanceAmount) < 0.01) {
                    continue
                }
                
                // Return first non-balance amount found
                return amount
            }
        }
        
        return null
    }
    
    private fun determineTransactionType(smsBody: String): TransactionType {
        val lowerBody = smsBody.lowercase()
        
        val hasDebitKeyword = DEBIT_KEYWORDS.any { lowerBody.contains(it) }
        val hasCreditKeyword = CREDIT_KEYWORDS.any { lowerBody.contains(it) }
        
        return when {
            hasDebitKeyword && !hasCreditKeyword -> TransactionType.DEBIT
            hasCreditKeyword && !hasDebitKeyword -> TransactionType.CREDIT
            else -> TransactionType.UNKNOWN
        }
    }
    
    private fun extractMerchant(smsBody: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim() ?: continue
                // Clean up merchant name
                return merchant
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("[^A-Za-z0-9\\s&-]"), "")
                    .trim()
            }
        }
        
        // Fallback: try to find capitalized words after common keywords
        val fallbackPattern = Pattern.compile("(?:at|to|for)\\s+([A-Z]+[A-Z0-9]*)", Pattern.CASE_INSENSITIVE)
        val matcher = fallbackPattern.matcher(smsBody)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        
        return null
    }
    
    private fun extractAccountNumber(smsBody: String): String? {
        val matcher = ACCOUNT_PATTERN.matcher(smsBody)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
    
    private fun determineBankName(sender: String): String {
        val normalizedSender = sender.uppercase()
        return when {
            normalizedSender.contains("HDFC") -> "HDFC Bank"
            normalizedSender.contains("ICICI") -> "ICICI Bank"
            normalizedSender.contains("SBI") -> "State Bank of India"
            normalizedSender.contains("AXIS") -> "Axis Bank"
            normalizedSender.contains("KOTAK") -> "Kotak Bank"
            normalizedSender.contains("PNB") -> "Punjab National Bank"
            normalizedSender.contains("BOI") -> "Bank of India"
            normalizedSender.contains("CANARA") -> "Canara Bank"
            normalizedSender.contains("UNION") -> "Union Bank"
            normalizedSender.contains("TMB") -> "Tamilnad Mercantile Bank"
            normalizedSender.contains("IDFC") -> "IDFC First Bank"
            normalizedSender.contains("YES") -> "Yes Bank"
            normalizedSender.contains("INDIAN") -> "Indian Bank"
            normalizedSender.contains("SC") -> "Standard Chartered"
            normalizedSender.contains("CITI") -> "Citibank"
            normalizedSender.contains("HSBC") -> "HSBC"
            normalizedSender.contains("DEUTSCHE") -> "Deutsche Bank"
            else -> "Bank"
        }
    }
}
