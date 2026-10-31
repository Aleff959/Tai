package com.tai.app.core

import dalvik.system.DexClassLoader

class ChildFirstDexClassLoader(
    dexPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    parent: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    @Synchronized
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Classes da ponte Host-Core, MainActivity e R vêm sempre do APK Pai (Host)
        val mustDelegateToParent = !name.startsWith("com.tai.app.") ||
            name == "com.tai.app.core.TaiCoreEntry" ||
            name.startsWith("com.tai.app.core.TaiCoreEntry$") ||
            name.startsWith("com.tai.app.core.CoreHostManager") ||
            name.startsWith("com.tai.app.core.ChildFirstDexClassLoader") ||
            name.startsWith("com.tai.app.MainActivity") ||
            name == "com.tai.app.R" ||
            name.startsWith("com.tai.app.R$")

        if (mustDelegateToParent) {
            return super.loadClass(name, resolve)
        }

        // 1. Verifica se já foi carregada por este ClassLoader filho
        var loadedClass = findLoadedClass(name)
        if (loadedClass == null) {
            try {
                // 2. Child-First: Busca primeiro no taicore.dex dinâmico!
                loadedClass = findClass(name)
            } catch (_: ClassNotFoundException) {
                // 3. Fallback seguro para o ClassLoader do APK pai
                loadedClass = parent.loadClass(name)
            }
        }

        if (resolve && loadedClass != null) {
            resolveClass(loadedClass)
        }
        return loadedClass ?: throw ClassNotFoundException(name)
    }
}
