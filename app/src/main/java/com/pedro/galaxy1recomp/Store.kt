package com.pedro.galaxy1recomp

import android.content.Context

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("g1", Context.MODE_PRIVATE)
    fun getBool(k: String, d: Boolean) = p.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    fun getInt(k: String, d: Int) = p.getInt(k, d)
    fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    fun getFloat(k: String, d: Float) = p.getFloat(k, d)
    fun putFloat(k: String, v: Float) = p.edit().putFloat(k, v).apply()
    fun getStr(k: String, d: String) = p.getString(k, d) ?: d
    fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    fun all(): Map<String, *> = p.all
    fun removeByPrefix(pre: String) {
        val e = p.edit()
        p.all.keys.filter { it.startsWith(pre) }.forEach { e.remove(it) }
        e.apply()
    }
}
