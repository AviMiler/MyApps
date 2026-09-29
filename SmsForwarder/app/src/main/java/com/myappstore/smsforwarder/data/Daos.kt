package com.myappstore.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Route>>

    @Query("SELECT * FROM routes ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<Route>

    @Query("SELECT * FROM routes WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun enabled(): List<Route>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun byId(id: Long): Route?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM routes")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(route: Route): Long

    @Update
    suspend fun update(route: Route)

    @Query("UPDATE routes SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: ForwardEvent): Long

    @Update
    suspend fun update(event: ForwardEvent)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): ForwardEvent?

    @Query("SELECT * FROM events WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ForwardEvent>

    @Query("SELECT * FROM events ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ForwardEvent>>

    /** Messages waiting for an undo window, a retry or the end of a hold. */
    @Query("SELECT * FROM events WHERE status IN (0, 1) ORDER BY scheduledAt ASC, id ASC")
    fun observeWaiting(): Flow<List<ForwardEvent>>

    @Query("SELECT * FROM events WHERE status IN (0, 1) AND scheduledAt <= :now ORDER BY scheduledAt ASC, id ASC")
    suspend fun due(now: Long): List<ForwardEvent>

    @Query("SELECT * FROM events WHERE status = 1 ORDER BY scheduledAt ASC, id ASC")
    suspend fun held(): List<ForwardEvent>

    @Query("SELECT MIN(scheduledAt) FROM events WHERE status IN (0, 1)")
    suspend fun nextScheduledAt(): Long?

    /** SMS handed to the radio since [since] - the basis of the daily limit. */
    @Query("SELECT COUNT(*) FROM events WHERE status IN (2, 3, 4) AND sentAt >= :since")
    suspend fun countSentSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM events WHERE status IN (2, 3, 4) AND sentAt >= :since")
    fun observeSentSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM events WHERE status IN (5, 8) AND receivedAt >= :since")
    fun observeProblemsSince(since: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM events WHERE routeId = :routeId AND kind = 0 " +
            "AND status IN (0, 1, 2, 3, 4) AND receivedAt >= :since",
    )
    suspend fun countRouteSince(routeId: Long, since: Long): Int

    @Query(
        "SELECT COUNT(*) FROM events WHERE sender = :sender AND body = :body AND recipient = :recipient " +
            "AND status IN (0, 1, 2, 3, 4) AND receivedAt >= :since",
    )
    suspend fun countDuplicates(sender: String, body: String, recipient: String, since: Long): Int

    /** Recently forwarded messages, used to find who a reply should go back to. */
    @Query(
        "SELECT * FROM events WHERE kind = 0 AND status IN (2, 3, 4) AND receivedAt >= :since " +
            "ORDER BY receivedAt DESC, id DESC LIMIT 300",
    )
    suspend fun recentForwards(since: Long): List<ForwardEvent>

    @Query(
        "SELECT routeId, COUNT(*) AS count, MAX(receivedAt) AS lastAt FROM events " +
            "WHERE kind = 0 AND status IN (3, 4) GROUP BY routeId",
    )
    fun observeRouteStats(): Flow<List<RouteStat>>

    @Query("UPDATE events SET status = :status, reason = :reason, detail = :detail WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, reason: Int, detail: String?)

    @Query("UPDATE events SET status = 3, sentAt = :at WHERE id IN (:ids) AND status = 2")
    suspend fun markSent(ids: List<Long>, at: Long)

    @Query("UPDATE events SET status = 4, deliveredAt = :at WHERE id IN (:ids) AND status IN (2, 3)")
    suspend fun markDelivered(ids: List<Long>, at: Long)

    @Query("UPDATE events SET scheduledAt = :at WHERE id IN (:ids) AND status IN (0, 1)")
    suspend fun reschedule(ids: List<Long>, at: Long)

    @Query("UPDATE events SET status = 7, reason = 17 WHERE status IN (0, 1)")
    suspend fun cancelAllWaiting(): Int

    @Query("DELETE FROM events WHERE receivedAt < :before AND status NOT IN (0, 1, 2)")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM events WHERE status NOT IN (0, 1, 2)")
    suspend fun clearFinished()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)
}
