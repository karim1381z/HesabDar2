package com.example.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.example.domain.JalaliCalendarUtils
import kotlinx.coroutines.flow.Flow

@Immutable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp", "id"], name = "idx_transactions_timestamp_id")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val storeName: String,
    val purpose: String,
    val totalAmountToman: Long,
    val userShareToman: Long,
    val dongAmountToman: Long = 0L,
    val dongFriendName: String = "",
    val weekdayDate: String, // e.g., "جمعه ۱ آبان"
    val timeStr: String,     // e.g., "۱۸:۱۰"
    val cardInfo: String,    // e.g., "کارت بلو ۶۲۱۹"
    val type: String,        // "EXPENSE", "INCOME", "SELF_TRANSFER", "GOLD_SAVE"
    val isUntagged: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val balanceAfterToman: Long? = null,
    val rawSmsBody: String = "",
    val bankName: String = ""
) {
    val timestampMillis: Long
        get() = timestamp

    @delegate:Ignore
    private val cachedJalali by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JalaliCalendarUtils.timestampToJalali(timestamp)
    }

    val jalaliDate: String
        get() = cachedJalali.dateWithYear

    val jalaliYear: Int
        get() = cachedJalali.year

    val jalaliMonth: Int
        get() = cachedJalali.month

    val jalaliDay: Int
        get() = cachedJalali.day

    val needsVoiceTag: Boolean
        get() = isUntagged

    @delegate:Ignore
    val subtitle: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "${cachedJalali.dateWithYear} • ${cachedJalali.timeStr} • $cardInfo"
    }

    @delegate:Ignore
    val shortBankName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val candidate = bankName.ifBlank {
            cardInfo
                .replace("کارت", "")
                .replace("حساب", "")
                .replace(digitsRegex, "")
                .trim()
        }
        if (candidate.isBlank() || candidate == "بانک" || candidate == "تراکنش صوتی") {
            "نقدی / دستی"
        } else {
            candidate
        }
    }

    companion object {
        private val digitsRegex = Regex("[۰-۹0-9]+")
    }

    @delegate:Ignore
    val simpleSubtitle: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "${cachedJalali.dateWithYear} • ${cachedJalali.timeStr} • $shortBankName"
    }

    @delegate:Ignore
    val displayTitle: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        when {
            !isUntagged && purpose.isNotBlank() &&
                purpose != "نیازمند تکمیل صوتی" &&
                purpose != "برداشت کارتی / خرید" &&
                purpose != "واریز به حساب" -> {
                if (storeName.isNotBlank() &&
                    storeName != "نامشخص" &&
                    storeName != "نقدی / دستی" &&
                    storeName != "تراکنش صوتی" &&
                    storeName != "بانک" &&
                    storeName != shortBankName &&
                    storeName != bankName &&
                    !purpose.contains(storeName)
                ) {
                    "$purpose ($storeName)"
                } else {
                    purpose
                }
            }
            else -> title
        }
    }
}

@Immutable
@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey val bankName: String, // e.g., "بلو", "سپه", "پاسارگاد", "ملت", "ویپاد"
    val nameEn: String = "",
    val balanceToman: Long = 0L,
    val isTankhah: Boolean = true,
    val cardLast4: String = "",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isManual: Boolean = false
)

@Immutable
@Entity(
    tableName = "commitments",
    indices = [
        Index(value = ["isSettled", "timestamp", "id"], name = "idx_commitments_settled_timestamp_id")
    ]
)
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "SAYYAD_ISSUED", "SAYYAD_RECEIVED", "LOAN_INSTALLMENT", "DONG"
    val title: String,
    val counterparty: String,
    val amountToman: Long,
    val dueDateStr: String,
    val sayyadId: String = "",
    val statusText: String,
    val isSettled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val dailySafeBudgetToman: Long = 0L,
    val monthlyBalanceToman: Long = 0L,
    val paydayStartDay: Int = 1,
    val paydayCycleLabel: String = "",
    val autoDetectSalary: Boolean = true,
    val goldPrice18kToman: Long = 4_360_000L,
    val goldReserveToman: Long = 0L,
    val goldGrams: Double = 0.0,
    val dailyAutoSaveEnabled: Boolean = true,
    val hideAmounts: Boolean = false,
    val onlyTankhahMode: Boolean = true,
    val tankhahModeEnabled: Boolean = false,
    val selectedFontFamily: String = "IRANSANSX"
)

@Dao
interface OrosiDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAllTransactionsNow(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txs: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTransactions(items: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("SELECT * FROM bank_accounts ORDER BY balanceToman DESC, lastUpdatedTimestamp DESC")
    fun observeBankAccounts(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts ORDER BY balanceToman DESC, lastUpdatedTimestamp DESC")
    suspend fun getAllBankAccountsNow(): List<BankAccountEntity>

    @Query("SELECT * FROM bank_accounts WHERE bankName = :bankName LIMIT 1")
    suspend fun getBankAccountByName(bankName: String): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBankAccount(account: BankAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBankAccounts(accounts: List<BankAccountEntity>)

    @Query("DELETE FROM bank_accounts WHERE bankName = :bankName")
    suspend fun deleteBankAccount(bankName: String)

    @Query("DELETE FROM bank_accounts")
    suspend fun clearAllBankAccounts()

    @Query("SELECT * FROM commitments ORDER BY isSettled ASC, timestamp DESC, id DESC")
    fun observeCommitments(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments ORDER BY isSettled ASC, timestamp DESC, id DESC")
    suspend fun getAllCommitmentsNow(): List<CommitmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitment(commitment: CommitmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitments(commitments: List<CommitmentEntity>)

    @Update
    suspend fun updateCommitment(commitment: CommitmentEntity)

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun deleteCommitmentById(id: Long)

    @Query("DELETE FROM commitments")
    suspend fun clearAllCommitments()

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsNow(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)

    @Transaction
    suspend fun purgeForRealSmsRescanAtomic(demoCommitmentIds: List<Long>) {
        clearAllTransactions()
        clearAllBankAccounts()
        demoCommitmentIds.forEach { deleteCommitmentById(it) }
    }

    @Transaction
    suspend fun restoreSnapshotAtomic(
        settings: AppSettingsEntity?,
        bankAccounts: List<BankAccountEntity>?,
        transactions: List<TransactionEntity>?,
        commitments: List<CommitmentEntity>?
    ) {
        if (settings != null) {
            saveSettings(settings)
        }
        if (bankAccounts != null) {
            clearAllBankAccounts()
            if (bankAccounts.isNotEmpty()) {
                upsertBankAccounts(bankAccounts)
            }
        }
        if (transactions != null) {
            clearAllTransactions()
            if (transactions.isNotEmpty()) {
                insertTransactions(transactions)
            }
        }
        if (commitments != null) {
            clearAllCommitments()
            if (commitments.isNotEmpty()) {
                insertCommitments(commitments)
            }
        }
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        BankAccountEntity::class,
        CommitmentEntity::class,
        AppSettingsEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class OrosiDatabase : RoomDatabase() {
    abstract fun orosiDao(): OrosiDao

    companion object {
        @Volatile
        private var INSTANCE: OrosiDatabase? = null

        internal val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN selectedFontFamily TEXT NOT NULL DEFAULT 'IRANSANSX'"
                )
            }
        }

        internal val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_transactions_timestamp_id ON transactions(timestamp, id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_commitments_settled_timestamp_id ON commitments(isSettled, timestamp, id)"
                )
            }
        }

        fun getInstance(context: Context): OrosiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrosiDatabase::class.java,
                    "orosi_finance_v15_jibeman.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
