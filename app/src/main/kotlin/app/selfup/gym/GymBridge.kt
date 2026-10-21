package app.selfup.gym

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat
import app.selfup.core.GymCardio
import app.selfup.core.GymSet
import app.selfup.core.GymWeighIn
import java.time.Instant
import java.time.LocalDate

/** Everything SelfUp reads from Gym in one pass. */
data class GymSnapshot(val sets: List<GymSet>, val cardio: List<GymCardio>, val weighIns: List<GymWeighIn>)

/**
 * The Android side of talking to the Gym app: is it installed, may we read it, and reading it.
 *
 * Gym shares a read-only log through a content provider guarded by a permission it defines
 * itself. That permission is "dangerous", so Android asks the user before granting it, and
 * Gym's debug and release builds each define their own (prefixed with their package name).
 */
class GymBridge(private val context: Context) {

    /** One installed build of Gym. */
    data class Install(val packageName: String) {
        val authority: String get() = "$packageName.training"
        val permission: String get() = "$packageName.permission.READ_TRAINING"
    }

    /** The installed build of Gym, preferring release over debug, or null. */
    fun find(): Install? = PACKAGES.map(::Install).firstOrNull { isInstalled(it.packageName) }

    /** False for a Gym from before sharing existed; it has to be updated first. */
    fun canShare(install: Install): Boolean =
        context.packageManager.resolveContentProvider(install.authority, 0) != null && isDefined(install.permission)

    fun isGranted(install: Install): Boolean =
        ContextCompat.checkSelfPermission(context, install.permission) == PackageManager.PERMISSION_GRANTED

    /** Reads the whole shared log. Throws if Gym refuses or can't be reached. */
    fun read(install: Install): GymSnapshot = GymSnapshot(
        sets = query(install, PATH_SETS) {
            GymSet(
                setId = long("set_id"),
                sessionId = long("session_id"),
                exercise = string("exercise"),
                weightKg = double("weight_kg"),
                reps = int("reps"),
                variation = string("variation"),
                assisted = int("assisted") != 0,
                loggedAt = Instant.ofEpochMilli(long("logged_at")),
            )
        },
        cardio = query(install, PATH_CARDIO) {
            GymCardio(
                id = long("id"),
                activity = string("activity"),
                minutes = int("minutes"),
                distanceKm = double("distance_km"),
                loggedAt = Instant.ofEpochMilli(long("logged_at")),
            )
        },
        weighIns = query(install, PATH_BODY_WEIGHT) {
            GymWeighIn(day = LocalDate.ofEpochDay(long("day")), weightKg = double("weight_kg"))
        },
    )

    private fun <T> query(install: Install, path: String, row: Cursor.() -> T): List<T> {
        val uri = Uri.parse("content://${install.authority}/$path")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: throw IllegalStateException("Gym returned nothing for $path")
        return cursor.use { buildList { while (it.moveToNext()) add(it.row()) } }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun isDefined(permission: String): Boolean = try {
        context.packageManager.getPermissionInfo(permission, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.double(column: String) = getDouble(getColumnIndexOrThrow(column))
    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

    companion object {
        /** Release first: if both are installed, the one used day to day is more likely the release. */
        val PACKAGES = listOf("dev.avles.gym", "dev.avles.gym.debug")

        /** Where Gym's latest debug build is always published. */
        const val DOWNLOAD_URL = "https://github.com/Selva461/Gym/releases/download/latest-debug/gym-debug.apk"

        private const val PATH_SETS = "v1/sets"
        private const val PATH_CARDIO = "v1/cardio"
        private const val PATH_BODY_WEIGHT = "v1/body_weight"
    }
}
