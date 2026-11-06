package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.AppErrorException
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.SafeCall
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import kotlin.random.Random

// The two invite-flow entry points: share (owner side) and join (partner
// side). Everything after these runs through SyncEngine like any other edit.
class ShareRepository(
    private val listDao: ShoppingListDao,
    private val remote: SharedListRemote,
    private val syncEngine: SyncEngine,
    private val currentUserId: () -> String?,
    private val safeCall: SafeCall,
    private val random: Random = Random.Default,
) {

    // Returns the list's share code, generating one on first share. The list
    // already syncs as an account list (docs/technical-decisions/008), so
    // sharing is just giving it a code: make sure the row is on the server,
    // then set the code there. Server first, local second: if that fails the
    // list stays unshared and share() is simply retried with a fresh code.
    suspend fun share(listId: String): Result<String> = safeCall {
        val list = listDao.getListRaw(listId)
            ?: throw AppErrorException(AppError.NotFoundError("That list no longer exists."))
        // Codes are static per list: re-opening the share dialog shows the
        // same one, so a code texted last week keeps working.
        list.shareCode?.let { return@safeCall it }
        currentUserId() ?: throw AppErrorException(AppError.AuthError("Sign in to share lists."))

        // A list made offline may not have reached the server yet. The push
        // uploads its items too; its failure is read off onServer below.
        if (!list.onServer) syncEngine.push()
        if (listDao.getListRaw(listId)?.onServer != true) {
            throw AppErrorException(AppError.NetworkError("Couldn't share — check your connection."))
        }

        // The server answers with the code it actually holds: if this list was
        // already shared (another phone, or an earlier attempt that died before
        // saving locally), the write-once trigger kept that code — adopt it.
        val code = remote.setShareCode(listId, generateCode())
            ?: throw AppErrorException(AppError.UnknownError())
        // A targeted UPDATE: never insertList (a REPLACE, which cascade-deletes
        // the items), and never a stale whole-row write over concurrent edits.
        listDao.setShareCode(listId, code)
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
