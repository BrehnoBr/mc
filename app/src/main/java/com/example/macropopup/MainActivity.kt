package com.example.macropopup

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var estado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()

        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        coluna.addView(TextView(this).apply {
            text = "Macro Popup"
            textSize = 24f
            setTextColor(Color.BLACK)
        })

        estado = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(12))
        }
        coluna.addView(estado)

        coluna.addView(TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF444444.toInt())
            text = "Como configurar:\n\n" +
                "1. Toque em \"Ativar serviço de acessibilidade\", escolha \"Macro Popup\" e ative.\n\n" +
                "2. Se o Android 13+ disser \"configuração restrita\": toque em " +
                "\"Configurações do app\", abra o menu ⋮ (canto superior) e escolha " +
                "\"Permitir configurações restritas\". Depois repita o passo 1.\n\n" +
                "3. O popup aparece sobre qualquer app. Arraste pelo cabeçalho, use – para " +
                "minimizar e ✕ para fechar (reabra por aqui).\n\n" +
                "Como usar:\n" +
                "• ● Gravar: faça toques e deslizes na tela. Toque em ■ para parar.\n" +
                "• Ajuste repetições (∞ = infinito) e velocidade com − e +.\n" +
                "• ▶ Reproduzir repete os gestos no ritmo gravado.\n\n" +
                "Limitações: grava um dedo por vez (sem pinça) e não grava teclado."
        })

        coluna.addView(botao("Ativar serviço de acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        coluna.addView(botao("Configurações do app (permitir restritas)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        coluna.addView(botao("Mostrar popup") {
            val s = MacroService.instance
            if (s == null) {
                Toast.makeText(this, "Ative o serviço de acessibilidade primeiro", Toast.LENGTH_LONG).show()
            } else {
                s.mostrarPainel()
            }
        })

        setContentView(ScrollView(this).apply { addView(coluna) })
    }

    override fun onResume() {
        super.onResume()
        val ativo = MacroService.instance != null
        estado.text = if (ativo) "✔ Serviço ativo" else "✖ Serviço desativado"
        estado.setTextColor(if (ativo) 0xFF1E8E3E.toInt() else 0xFFD93025.toInt())
    }

    private fun botao(texto: String, acao: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() }
        setOnClickListener { acao() }
    }
}
