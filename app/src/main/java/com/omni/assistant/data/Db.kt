package com.omni.assistant.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** "user" or "assistant". */
    val role: String,
    val content: String,
    val createdAt: Long,
    val failed: Boolean = false,
    val error: String? = null
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    /** "image", "video" (a folder of sampled frames) or "file". */
    val kind: String,
    val name: String,
    val mime: String,
    /** File path (image/file) or directory path (video frames) inside the app's private storage. */
    val path: String,
    val thumbPath: String?,
    val sizeBytes: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun rename(conversationId: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :conversationId")
    suspend fun touch(conversationId: Long, time: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAttachments(items: List<AttachmentEntity>)

    @Query(
        "SELECT a.* FROM attachments a INNER JOIN messages m ON a.messageId = m.id " +
            "WHERE m.conversationId = :conversationId ORDER BY a.id ASC"
    )
    fun observeAttachments(conversationId: Long): Flow<List<AttachmentEntity>>

    @Query(
        "SELECT a.* FROM attachments a INNER JOIN messages m ON a.messageId = m.id " +
            "WHERE m.conversationId = :conversationId ORDER BY a.id ASC"
    )
    suspend fun getAttachments(conversationId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun getAllAttachments(): List<AttachmentEntity>
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, AttachmentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        /** v1 -> v2: adds the attachments table. Existing chats are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`messageId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`mime` TEXT NOT NULL, `path` TEXT NOT NULL, `thumbPath` TEXT, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_messageId` ON `attachments` (`messageId`)")
            }
        }
    }
}
