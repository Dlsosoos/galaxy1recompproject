package com.pedro.galaxy1recomp

sealed class Opt(val key: String, val title: String)
class Sw(key: String, title: String, val def: Boolean) : Opt(key, title)
class Sl(key: String, title: String, val min: Float, val max: Float, val def: Float, val steps: Int = 0, val unit: String = "") : Opt(key, title)
class Ch(key: String, title: String, val items: List<String>, val def: Int) : Opt(key, title)

object Settings {
    val sections: List<Pair<String, List<Opt>>> = listOf(
        "Vídeo" to listOf(
            Ch("cfg_backend", "Backend gráfico", listOf("Vulkan", "OpenGL ES"), 0),
            Sl("cfg_res", "Escala de resolução", 1f, 4f, 1f, 5, "x"),
            Ch("cfg_aspect", "Proporção de tela", listOf("Original 4:3", "Widescreen 16:9", "Esticar"), 1),
            Ch("cfg_aniso", "Filtragem anisotrópica", listOf("Off", "2x", "4x", "8x", "16x"), 0),
            Ch("cfg_aa", "Anti-aliasing", listOf("Off", "MSAA 2x", "MSAA 4x"), 0),
            Ch("cfg_fpslimit", "Limite de FPS", listOf("30", "60", "Ilimitado"), 1),
            Sw("cfg_vsync", "VSync", true),
            Sw("cfg_showfps", "Mostrar FPS", false),
            Sw("cfg_shadercache", "Cache de shaders", true),
            Sw("cfg_hdtex", "Texturas HD (pacotes de mods)", true),
            Sw("cfg_bloom", "Bloom", true),
            Sl("cfg_brightness", "Brilho", 50f, 150f, 100f, 0, "%")
        ),
        "Áudio" to listOf(
            Sl("cfg_vol", "Volume geral", 0f, 100f, 80f, 0, "%"),
            Sl("cfg_vol_music", "Volume da música", 0f, 100f, 100f, 0, "%"),
            Sl("cfg_vol_sfx", "Volume dos efeitos", 0f, 100f, 100f, 0, "%"),
            Ch("cfg_audiolat", "Latência de áudio", listOf("Baixa", "Média", "Alta"), 1),
            Ch("cfg_audiomode", "Saída", listOf("Estéreo", "Surround (Dolby Pro Logic II)"), 0),
            Sw("cfg_mutebg", "Silenciar em segundo plano", true)
        ),
        "Controles" to listOf(
            Ch("cfg_ctrlmode", "Modo de controle", listOf("Toque (Wii Remote virtual)", "Giroscópio", "Gamepad físico"), 0),
            Sw("cfg_overlay", "Mostrar controles na tela", true),
            Sl("cfg_overlay_alpha", "Opacidade dos controles", 10f, 100f, 60f, 0, "%"),
            Sl("cfg_overlay_size", "Tamanho dos controles", 50f, 150f, 100f, 0, "%"),
            Sl("cfg_pointer_sens", "Sensibilidade do ponteiro", 1f, 10f, 5f, 8),
            Sl("cfg_gyro_sens", "Sensibilidade do giroscópio", 1f, 10f, 5f, 8),
            Ch("cfg_spin", "Girar (Spin)", listOf("Botão na tela", "Agitar o celular", "Deslizar na tela"), 0),
            Sw("cfg_invert_y", "Inverter eixo Y", false),
            Sw("cfg_haptics", "Vibração", true),
            Sw("cfg_costar", "Co-Star (2º jogador no ponteiro)", false)
        ),
        "Jogo" to listOf(
            Ch("cfg_lang", "Idioma", listOf("Português", "Inglês", "Espanhol", "Francês", "Alemão", "Italiano", "Japonês"), 1),
            Sl("cfg_overclock", "Overclock da CPU", 100f, 300f, 100f, 3, "%"),
            Sw("cfg_autosave", "Salvar automaticamente", true),
            Ch("cfg_autosave_int", "Intervalo do autosave", listOf("1 min", "5 min", "10 min"), 1),
            Sw("cfg_smoothcam", "Câmera suavizada", true),
            Sw("cfg_preload", "Pré-carregar disco na RAM", false)
        ),
        "Avançado" to listOf(
            Ch("cfg_threads", "Threads de CPU", listOf("Auto", "2", "4", "6"), 0),
            Ch("cfg_perf", "Modo de desempenho", listOf("Economia", "Equilibrado", "Alto"), 1),
            Sw("cfg_debuglog", "Log de depuração", false),
            Sw("cfg_diskcache", "Cache de disco", true)
        )
    )

    fun buildConfig(store: Store): String =
        sections.flatMap { it.second }.joinToString(";") { o ->
            val v: Any = when (o) {
                is Sw -> store.getBool(o.key, o.def)
                is Sl -> store.getFloat(o.key, o.def)
                is Ch -> store.getInt(o.key, o.def)
            }
            "${o.key}=$v"
        }

    fun reset(store: Store) = store.removeByPrefix("cfg_")
}
