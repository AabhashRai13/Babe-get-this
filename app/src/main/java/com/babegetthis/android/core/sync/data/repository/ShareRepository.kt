package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.AppErrorException
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.sync.data.mapper.toRow
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import kotlin.random.Random

// The two invite-flow entry points: share (owner side) and join (partner
// side). Everything after these runs through SyncEngine like any other edit.
class ShareRepository(
    private val listDao: ShoppingListDao,
    private val itemDao: ShoppingItemDao,
    private val remote: SharedListRemote,
    private val syncEngine: SyncEngine,
    private val currentUserId: () -> String?,
    private val random: Random = Random.Default,
) {

    // Returns the list's share code, generating one on first share. Server
    // first, local second: if the upload fails the list stays local-only and
    // share() is simply retried (with a fresh code — the failed one was never
    // persisted anywhere).
    suspend fun share(listId: String): Result<String> = safeCall {
        val list = listDao.getListRaw(listId)
            ?: throw AppErrorException(AppError.NotFoundError("That list no longer exists."))
        // Codes are static per list: re-opening the share dialog shows the
        // same one, so a code texted last week keeps working.
        list.shareCode?.let { return@safeCall it }
        val userId = currentUserId()
            ?: throw AppErrorException(AppError.AuthError("Sign in to share lists."))

        val generated = generateCode()
        val inserted = remote.insertList(list.copy(shareCode = generated).toRow(createdBy = userId))
        val code = if (inserted) {
            generated
        } else {
            // A previous share() reached the server but died before saving the
            // code locally. The server row is the truth — adopt its code. We
            // can read it because that half-share already made us a member.
            remote.fetchList(listId, sinceIso = null).firstOrNull()?.shareCode
                ?: throw AppErrorException(AppError.UnknownError())
        }
        val items = itemDao.getItemsByListIdOnce(listId)
        if (items.isNotEmpty()) {
            remote.upsertItems(items.map { it.toRow() })
        }
        listDao.insertList(list.copy(shareCode = code))
        code
    }

    // Enter a code → become a member → land the full replica in Room. The
    // pull is a FULL catch-up (sync point dropped first): on a first join
    // that's simply the initial pull, and on a re-join it refreshes the
    // existing replica instead of silently no-oping from an already-current
    // high-water mark — re-entering the code is also the user's recovery
    // path when a replica has gone bad.
    suspend fun join(code: String): Result<String> = safeCall {
        val listId = remote.joinListByCode(code.trim())
            ?: throw AppErrorException(AppError.NotFoundError("That code didn't match any list."))
        when (val pull = syncEngine.fullCatchUp(listId)) {
            is Result.Error -> throw AppErrorException(pull.error)
            is Result.Success -> listId
        }
    }

    private fun generateCode(): String =
        buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    private companion object {
        // Matches the join RPC's normalisation: uppercase, and no 0/O/1/I —
        // they read ambiguously in a texted code. 32^6 ≈ 1e9 codes; the DB
        // unique constraint backstops the astronomically unlikely collision
        // (share() fails, retry generates a fresh code).
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 6
    }
}
