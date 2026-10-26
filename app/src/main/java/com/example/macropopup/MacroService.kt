package com.example.macropopup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max

/** Um toque/deslize gravado: quando começou (ms), quanto durou (ms) e o caminho (x0,y0,x1,y1,...). */
class Gesto(val t: Long, val dur: Long, val pts: FloatArray)

class MacroService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: MacroService? = null

        val REPETICOES = intArrayOf(1, 2, 3, 5, 10, 20, 50, 100, 0) // 0 = infinito
        val VELOCIDADES = doubleArrayOf(0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0)
    }

    private lateinit var wm: WindowManager
    private val playHandler = Handler(Looper.getMainLooper())

    private var gestos = mutableListOf<Gesto>()
    private var gravando = false
    private var reproduzindo = false
    private var inicioGravacao = 0L
    private var ciclo = 0
    private var idxRep = 0
    private var idxVel = 2

    // ----- painel flutuante -----
    private var painel: View? = null
    private var painelLp: WindowManager.LayoutParams? = null
    private var corpo: View? = null
    private var tvTitulo: TextView? = null
    private var tvStatus: TextView? = null
    private var tvInfo: TextView? = null
    private var tvRep: TextView? = null
    private var tvVel: TextView? = null
    private var btnGravar: Button? = null
    private var btnReproduzir: Button? = null

    // ----- captura de gravação -----
    private var gravView: View? = null
    private var gravLp: WindowManager.LayoutParams? = null
    private var atual: MutableList<Float>? = null
    private var atualInicio = 0L

    // ================= CICLO DE VIDA =================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        carregar()
        mostrarPainel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        pararReproducao()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        limpar()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        limpar()
        super.onDestroy()
    }

    private fun limpar() {
        pararReproducao()
        gravando = false
        gravView?.let { remover(it) }
        gravView = null
        painel?.let { remover(it) }
        painel = null
        instance = null
    }

    private fun remover(v: View) {
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    // ================= PAINEL (POPUP) =================

    fun mostrarPainel() {
        if (painel != null) return

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(230)
            setPadding(dp(10), dp(6), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2202124.toInt())
                cornerRadius = dp(16).toFloat()
            }
        }

        // Cabeçalho (arraste por aqui)
        val cabecalho = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titulo = TextView(this).apply {
            text = "⠿ Macro"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, dp(6), 0, dp(6))
        }
        tvTitulo = titulo
        cabecalho.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cabecalho.addView(botaoPequeno("–") { alternarMinimizar() })
        cabecalho.addView(botaoPequeno("✕") { fecharPainel() })
        raiz.addView(cabecalho)

        // Corpo
        val corpoLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        corpo = corpoLayout

        val status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f }
        val info = TextView(this).apply { setTextColor(0xFFBDC1C6.toInt()); textSize = 12f }
        tvStatus = status
        tvInfo = info
        corpoLayout.addView(status)
        corpoLayout.addView(info)

        val bGravar = botao("● Gravar", 0xFFD93025.toInt()) { alternarGravacao() }
        btnGravar = bGravar
        corpoLayout.addView(bGravar, margem(top = 8))

        val rep = TextView(this)
        tvRep = rep
        corpoLayout.addView(
            stepper(rep, { mudarRepeticoes(-1) }, { mudarRepeticoes(+1) }),
            margem(top = 8)
        )

        val vel = TextView(this)
        tvVel = vel
        corpoLayout.addView(
            stepper(vel, { mudarVelocidade(-1) }, { mudarVelocidade(+1) }),
            margem(top = 6)
        )

        val bPlay = botao("▶ Reproduzir", 0xFF1E8E3E.toInt()) { alternarReproducao() }
        btnReproduzir = bPlay
        corpoLayout.addView(bPlay, margem(top = 8))

        raiz.addView(corpoLayout)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(140)
        }

        // Arrastar o popup pelo cabeçalho
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        cabecalho.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ix + (e.rawX - tx).toInt()
                    lp.y = iy + (e.rawY - ty).toInt()
                    try {
                        wm.updateViewLayout(raiz, lp)
                    } catch (_: Exception) {
                    }
                }
            }
            true
        }

        painel = raiz
        painelLp = lp
        wm.addView(raiz, lp)
        atualizarUi()
    }

    private fun fecharPainel() {
        pararReproducao()
        if (gravando) pararGravacao()
        painel?.let { remover(it) }
        painel = null
        painelLp = null
    }

    private fun alternarMinimizar() {
        val c = corpo ?: return
        val minimizado = c.visibility == View.VISIBLE
        c.visibility = if (minimizado) View.GONE else View.VISIBLE
        tvTitulo?.text = if (minimizado) "⠿ ●" else "⠿ Macro"
    }

    /** Recoloca o painel acima da camada de gravação (a última janela adicionada fica por cima). */
    private fun trazerPainelParaFrente() {
        val v = painel ?: return
        val lp = painelLp ?: return
        remover(v)
        try {
            wm.addView(v, lp)
        } catch (_: Exception) {
        }
    }

    private fun margem(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun fundo(cor: Int) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = dp(10).toFloat()
    }

    private fun botao(texto: String, cor: Int, acao: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 14f
        minHeight = 0
        minimumHeight = dp(42)
        background = fundo(cor)
        setOnClickListener { acao() }
    }

    private fun botaoPequeno(texto: String, acao: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 14f
        minHeight = 0
        minimumHeight = dp(32)
        minWidth = 0
        minimumWidth = dp(36)
        setPadding(0, 0, 0, 0)
        background = fundo(0x00000000)
        setOnClickListener { acao() }
    }

    private fun stepper(rotulo: TextView, menos: () -> Unit, mais: () -> Unit): LinearLayout {
        rotulo.setTextColor(Color.WHITE)
        rotulo.textSize = 13f
        rotulo.gravity = Gravity.CENTER
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(botao("−", 0xFF3C4043.toInt(), menos).apply { minimumWidth = dp(44); minWidth = 0 })
            addView(rotulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(botao("+", 0xFF3C4043.toInt(), mais).apply { minimumWidth = dp(44); minWidth = 0 })
        }
    }

    private fun textoVelocidade(v: Double): String =
        if (v == v.toInt().toDouble()) "${v.toInt()}x" else "${v}x"

    private fun atualizarUi() {
        tvStatus?.text = when {
            gravando -> "● Gravando… toque na tela"
            reproduzindo -> "▶ Reproduzindo (ciclo ${ciclo + 1})"
            else -> "Pronto"
        }
        tvInfo?.text = "Gestos gravados: ${gestos.size}"
        btnGravar?.text = if (gravando) "■ Parar gravação" else "● Gravar"
        btnReproduzir?.text = if (reproduzindo) "■ Parar" else "▶ Reproduzir"
        val r = REPETICOES[idxRep]
        tvRep?.text = "Repetições: ${if (r == 0) "∞" else r.toString()}"
        tvVel?.text = "Velocidade: ${textoVelocidade(VELOCIDADES[idxVel])}"
    }

    private fun mudarRepeticoes(delta: Int) {
        idxRep = (idxRep + delta).coerceIn(0, REPETICOES.size - 1)
        atualizarUi()
    }

    private fun mudarVelocidade(delta: Int) {
        idxVel = (idxVel + delta).coerceIn(0, VELOCIDADES.size - 1)
        atualizarUi()
    }

    // ================= GRAVAÇÃO =================

    private fun alternarGravacao() {
        if (gravando) pararGravacao() else iniciarGravacao()
    }

    private fun iniciarGravacao() {
        if (reproduzindo) {
            aviso("Pare a reprodução antes de gravar")
            return
        }
        gestos = mutableListOf()
        gravando = true
        inicioGravacao = SystemClock.uptimeMillis()

        // Camada transparente (levemente avermelhada) que captura os toques.
        val v = View(this)
        v.setBackgroundColor(0x14FF0000)
        v.setOnTouchListener { _, e ->
            aoTocarGravando(e)
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        gravView = v
        gravLp = lp

        trazerPainelParaFrente()
        atualizarUi()
    }

    private fun pararGravacao() {
        gravando = false
        atual = null
        gravView?.let { remover(it) }
        gravView = null
        gravLp = null

        // Remove o tempo ocioso antes do primeiro toque (mantém os intervalos entre gestos).
        if (gestos.isNotEmpty()) {
            val base = gestos[0].t
            gestos = gestos.map { Gesto(it.t - base, it.dur, it.pts) }.toMutableList()
        }
        salvar()
        atualizarUi()
    }

    private fun aoTocarGravando(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                atual = mutableListOf(e.rawX, e.rawY)
                atualInicio = e.eventTime
            }

            MotionEvent.ACTION_MOVE -> {
                val l = atual ?: return
                val n = l.size
                if (n < 800 && hypot(e.rawX - l[n - 2], e.rawY - l[n - 1]) >= 6f) {
                    l.add(e.rawX)
                    l.add(e.rawY)
                }
            }

            MotionEvent.ACTION_UP -> {
                val l = atual ?: return
                atual = null
                val n = l.size
                if (e.rawX != l[n - 2] || e.rawY != l[n - 1]) {
                    l.add(e.rawX)
                    l.add(e.rawY)
                }
                val dur = max(1L, e.eventTime - atualInicio)
                val g = Gesto(atualInicio - inicioGravacao, dur, l.toFloatArray())
                gestos.add(g)
                atualizarUi()
                repassarAoApp(g)
            }

            MotionEvent.ACTION_CANCEL -> atual = null
        }
    }

    /**
     * A camada de gravação "engole" o toque. Para o app de baixo reagir normalmente,
     * o mesmo gesto é reenviado logo após o dedo levantar, com a camada intocável.
     */
    private fun repassarAoApp(g: Gesto) {
        val v = gravView ?: return
        val lp = gravLp ?: return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            wm.updateViewLayout(v, lp)
        } catch (_: Exception) {
            return
        }
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = liberarToques()
            override fun onCancelled(gestureDescription: GestureDescription?) = liberarToques()
        }
        val ok = try {
            dispatchGesture(montar(g, 1.0), cb, null)
        } catch (_: Exception) {
            false
        }
        if (!ok) liberarToques()
    }

    private fun liberarToques() {
        val v = gravView ?: return
        val lp = gravLp ?: return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try {
            wm.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    // ================= REPRODUÇÃO =================

    private fun montar(g: Gesto, velocidade: Double): GestureDescription {
        val p = Path()
        p.moveTo(g.pts[0], g.pts[1])
        var i = 2
        while (i + 1 < g.pts.size) {
            p.lineTo(g.pts[i], g.pts[i + 1])
            i += 2
        }
        val maxDur = GestureDescription.getMaxGestureDuration()
        val dur = (g.dur / velocidade).toLong().coerceIn(1L, maxDur)
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, dur))
            .build()
    }

    private fun alternarReproducao() {
        if (reproduzindo) {
            pararReproducao()
            return
        }
        if (gravando) pararGravacao()
        if (gestos.isEmpty()) {
            aviso("Grave uma sequência primeiro")
            return
        }
        reproduzindo = true
        ciclo = 0
        iniciarCiclo()
        atualizarUi()
    }

    private fun iniciarCiclo() {
        val vel = VELOCIDADES[idxVel]
        for (g in gestos) {
            playHandler.postDelayed({
                if (reproduzindo) dispatchGesture(montar(g, vel), null, null)
            }, (g.t / vel).toLong())
        }
        val fim = gestos.maxOf { it.t + it.dur } / vel + 200.0
        playHandler.postDelayed({ fimDoCiclo() }, fim.toLong())
    }

    private fun fimDoCiclo() {
        if (!reproduzindo) return
        ciclo++
        val r = REPETICOES[idxRep]
        if (r != 0 && ciclo >= r) {
            pararReproducao()
        } else {
            iniciarCiclo()
            atualizarUi()
        }
    }

    private fun pararReproducao() {
        reproduzindo = false
        ciclo = 0
        playHandler.removeCallbacksAndMessages(null)
        atualizarUi()
    }

    // ================= PERSISTÊNCIA =================

    private fun salvar() {
        val arr = JSONArray()
        for (g in gestos) {
            val o = JSONObject()
            o.put("t", g.t)
            o.put("d", g.dur)
            val p = JSONArray()
            for (f in g.pts) p.put(f.toDouble())
            o.put("p", p)
            arr.put(o)
        }
        getSharedPreferences("macro", MODE_PRIVATE).edit().putString("gestos", arr.toString()).apply()
    }

    private fun carregar() {
        val s = getSharedPreferences("macro", MODE_PRIVATE).getString("gestos", null) ?: return
        try {
            val arr = JSONArray(s)
            val lista = mutableListOf<Gesto>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = o.getJSONArray("p")
                lista.add(Gesto(o.getLong("t"), o.getLong("d"), FloatArray(p.length()) { p.getDouble(it).toFloat() }))
            }
            gestos = lista
        } catch (_: Exception) {
        }
    }
}
