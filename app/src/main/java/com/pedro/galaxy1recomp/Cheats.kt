package com.pedro.galaxy1recomp

data class Cheat(val id: String, val title: String, val desc: String)

object Cheats {
    val unlockAll = Cheat(
        "unlock_all", "Desbloquear tudo (save completo)",
        "120 estrelas, todas as galáxias e cometas, Luigi liberado, Grand Finale e final verdadeiro"
    )
    val list = listOf(
        Cheat("infinite_lives", "Vidas infinitas", "Nunca perde vidas"),
        Cheat("infinite_health", "Vida infinita", "Barra de vida sempre cheia"),
        Cheat("max_starbits", "Star Bits no máximo", "9999 Star Bits"),
        Cheat("infinite_air", "Ar infinito", "Sem limite de oxigênio debaixo d'água"),
        Cheat("luigi", "Luigi liberado", "Jogar com o Luigi desde o início"),
        Cheat("all_comets", "Todos os cometas", "Libera todos os desafios de cometa"),
        Cheat("skip_cutscenes", "Pular cutscenes", "Pula cenas já vistas"),
        Cheat("moon_jump", "Pulo lunar", "Pulos mais altos")
    )

    fun build(store: Store): String =
        (listOf(unlockAll) + list).filter { store.getBool("cheat_" + it.id, false) }.joinToString(",") { it.id }
}
