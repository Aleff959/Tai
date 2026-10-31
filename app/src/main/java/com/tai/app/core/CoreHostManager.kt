package com.tai.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream

object CoreHostManager {
    private const val TAG = "TaiBootGuard"
    private const val PREFS_NAME = "tai_bootguard_prefs"
    private const val KEY_ACTIVE_FILE = "active_dex_file"
    private const val KEY_ACTIVE_COMP = "active_comp"
    private const val KEY_BACKUP_FILE = "backup_dex_file"
    private const val KEY_BACKUP_COMP = "backup_comp"
    private const val KEY_PENDING_FILE = "pending_dex_file"
    private const val KEY_PENDING_COMP = "pending_comp"
    private const val KEY_LAST_APK_COMP = "last_apk_comp"

    fun getCoreDir(context: Context): File {
        return File(context.filesDir, "tai_core").apply { mkdirs() }
    }

    fun getPendingTempFile(context: Context, comp: Int): File {
        return File(getCoreDir(context), "taicore_pending_v$comp.dex")
    }

    fun verifyDexMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 8192L) return false
        return try {
            val header = ByteArray(4)
            FileInputStream(file).use { fis ->
                if (fis.read(header) != 4) return false
            }
            // Magic header padrão de todo arquivo DEX: "dex\n" (0x64, 0x65, 0x78, 0x0A)
            header[0] == 0x64.toByte() &&
                header[1] == 0x65.toByte() &&
                header[2] == 0x78.toByte() &&
                header[3] == 0x0A.toByte()
        } catch (_: Exception) {
            false
        }
    }

    fun stagePendingUpdate(context: Context, comp: Int, stagedFile: File): Boolean {
        if (!verifyDexMagic(stagedFile)) {
            Log.e(TAG, "BootGuard: Arquivo DEX inválido ou corrompido rejeitado: ${stagedFile.name}")
            stagedFile.delete()
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_FILE, stagedFile.name)
            .putInt(KEY_PENDING_COMP, comp)
            .commit()
        Log.i(TAG, "BootGuard: Módulo v$comp validado e engatilhado para swap (${stagedFile.length()} bytes)")
        return true
    }

    fun hasPendingUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingName = prefs.getString(KEY_PENDING_FILE, null) ?: return false
        val pendingFile = File(getCoreDir(context), pendingName)
        return verifyDexMagic(pendingFile)
    }

    fun getActiveCompilation(context: Context, builtInComp: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeComp = prefs.getInt(KEY_ACTIVE_COMP, 0)
        return maxOf(builtInComp, activeComp)
    }

    private fun applyPendingSwapIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingName = prefs.getString(KEY_PENDING_FILE, null) ?: return
        val pendingComp = prefs.getInt(KEY_PENDING_COMP, 0)
        val coreDir = getCoreDir(context)
        val pendingFile = File(coreDir, pendingName)

        if (!verifyDexMagic(pendingFile) || pendingComp <= 0) {
            pendingFile.delete()
            prefs.edit().remove(KEY_PENDING_FILE).remove(KEY_PENDING_COMP).commit()
            return
        }

        try {
            val targetName = "taicore_v$pendingComp.dex"
            val targetFile = File(coreDir, targetName)
            if (targetFile.exists()) {
                targetFile.setWritable(true)
                targetFile.delete()
            }

            pendingFile.copyTo(targetFile, overwrite = true)
            pendingFile.delete()
            // Android 14+ (API 34+) exige permissão somente-leitura antes de carregar no DexClassLoader
            targetFile.setReadOnly()

            val previousActiveFile = prefs.getString(KEY_ACTIVE_FILE, null)
            val previousActiveComp = prefs.getInt(KEY_ACTIVE_COMP, 0)

            val editor = prefs.edit()
            if (previousActiveFile != null && previousActiveFile != targetName && previousActiveComp > 0) {
                editor.putString(KEY_BACKUP_FILE, previousActiveFile)
                editor.putInt(KEY_BACKUP_COMP, previousActiveComp)
            }
            editor.putString(KEY_ACTIVE_FILE, targetName)
            editor.putInt(KEY_ACTIVE_COMP, pendingComp)
            editor.remove(KEY_PENDING_FILE)
            editor.remove(KEY_PENDING_COMP)
            editor.commit()

            Log.i(TAG, "BootGuard: Swap atômico concluído -> $targetName (Comp. $pendingComp)")
        } catch (e: Exception) {
            Log.e(TAG, "BootGuard: Erro durante swap atômico", e)
        }
    }

    fun loadActiveCore(context: Context, builtInProvider: () -> TaiCoreEntry): TaiCoreEntry {
        applyPendingSwapIfNeeded(context)

        val builtInEntry = builtInProvider()
        val builtInComp = builtInEntry.getCompilationNumber()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Se o APK pai foi atualizado via cabo/instalador com uma versão superior ao último APK registrado
        val lastApkComp = prefs.getInt(KEY_LAST_APK_COMP, 0)
        if (builtInComp > lastApkComp) {
            prefs.edit().putInt(KEY_LAST_APK_COMP, builtInComp).commit()
            if (prefs.getInt(KEY_ACTIVE_COMP, 0) <= builtInComp) {
                prefs.edit()
                    .remove(KEY_ACTIVE_FILE)
                    .putInt(KEY_ACTIVE_COMP, builtInComp)
                    .commit()
            }
        }

        val activeComp = prefs.getInt(KEY_ACTIVE_COMP, 0)
        val activeFileName = prefs.getString(KEY_ACTIVE_FILE, null)
        val coreDir = getCoreDir(context)

        if (activeComp >= builtInComp && !activeFileName.isNullOrEmpty()) {
            val activeFile = File(coreDir, activeFileName)
            if (verifyDexMagic(activeFile)) {
                try {
                    return loadFromDexFile(context, activeFile, activeComp)
                } catch (t: Throwable) {
                    Log.e(TAG, "BootGuard: Falha ao carregar módulo ativo ($activeFileName). Iniciando Rollback!", t)
                    activeFile.setWritable(true)
                    activeFile.delete()

                    // Tenta rollback para o módulo de backup (SwitchCast BootGuard)
                    val backupFileName = prefs.getString(KEY_BACKUP_FILE, null)
                    val backupComp = prefs.getInt(KEY_BACKUP_COMP, 0)
                    if (!backupFileName.isNullOrEmpty() && backupComp >= builtInComp) {
                        val backupFile = File(coreDir, backupFileName)
                        if (verifyDexMagic(backupFile)) {
                            try {
                                val recovered = loadFromDexFile(context, backupFile, backupComp)
                                prefs.edit()
                                    .putString(KEY_ACTIVE_FILE, backupFileName)
                                    .putInt(KEY_ACTIVE_COMP, backupComp)
                                    .commit()
                                Log.i(TAG, "BootGuard: Rollback para backup ($backupFileName) concluído com sucesso!")
                                return recovered
                            } catch (t2: Throwable) {
                                Log.e(TAG, "BootGuard: Backup também falhou, restaurando APK base.", t2)
                            }
                        }
                    }

                    // Fallback final para o Core embutido no APK Pai
                    prefs.edit()
                        .remove(KEY_ACTIVE_FILE)
                        .putInt(KEY_ACTIVE_COMP, builtInComp)
                        .commit()
                }
            }
        }

        Log.i(TAG, "BootGuard: Utilizando Core nativo do APK Pai (Comp. $builtInComp)")
        return builtInEntry
    }

    private fun loadFromDexFile(context: Context, dexFile: File, comp: Int): TaiCoreEntry {
        // Requisito de segurança do Android 14/15 (API 34+): arquivo DEX deve ser somente-leitura
        dexFile.setReadOnly()

        val optDir = File(context.codeCacheDir, "tai_opt_v$comp").apply { mkdirs() }
        val classLoader = ChildFirstDexClassLoader(
            dexPath = dexFile.absolutePath,
            optimizedDirectory = optDir.absolutePath,
            librarySearchPath = context.applicationInfo.nativeLibraryDir,
            parent = context.classLoader
        )

        val implClass = classLoader.loadClass("com.tai.app.core.TaiCoreEntryImpl")
        val instance = implClass.getDeclaredConstructor().newInstance() as TaiCoreEntry
        Log.i(
            TAG,
            "BootGuard: Core Filho carregado em memória -> ${instance.getVersionName()} | Comp. ${instance.getCompilationNumber()} - ${instance.getCodename()}"
        )
        return instance
    }
}
