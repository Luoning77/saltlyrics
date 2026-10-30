package com.luoning.saltlyrics.ui

import com.luoning.saltlyrics.*
import com.luoning.saltlyrics.playback.SessionLink
import com.luoning.saltlyrics.playback.salt.SaltConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.roundToInt

/** Native options screen. All player controls live here, with no separate overlay/panel. */
class LyricsSettingsScreen(private val parent: Screen?) : Screen(tr("saltlyrics.settings")) {
    companion object { fun tr(key: String,vararg args: Any): Component=Component.translatable(key,*args) }
    private var page=0
    private var port: EditBox?=null
    private var progress: ProgressSlider?=null
    private var shownTrackKey: String?=null
    private var feedback: Component?=null
    private val controls=mutableListOf<Button>()
    private var top=52
    private var draftPort=SaltConfig.port.toString()
    private var maskedToken=""
    private fun button(label: Component,x: Int,y: Int,w: Int=150,action: ()->Unit): Button =
        addRenderableWidget(Button.builder(label) { action() }.bounds(x,y,w,20).build())
    private fun toggle(key: String,row: Int,col: Int,get: ()->Boolean,set: (Boolean)->Unit) {
        val b=button(tr(key,tr(if(get()) "options.on" else "options.off")),width/2-154+col*158,top+row*24) {
            set(!get()); SaltLyricsClient.renderer.clear(); rebuildWidgets()
        }
        b.tooltip=Tooltip.create(tr("$key.tip"))
    }
    private fun slider(key: String,row: Int,col: Int,min: Double,max: Double,initial: Double,step: Double,set: (Double)->Unit) {
        addRenderableWidget(object : AbstractSliderButton(width/2-154+col*158,top+row*24,150,20,Component.empty(),(initial-min)/(max-min)) {
            init { updateMessage() }
            private fun actual()=(min+value*(max-min)).let { (it/step).roundToInt()*step }.coerceIn(min,max)
            override fun updateMessage() {
                message=tr(key,if(step>=1) actual().roundToInt().toString() else String.format(Locale.ROOT,"%.2f",actual()))
            }
            override fun applyValue() { set(actual()); SaltLyricsClient.renderer.clear() }
        })
    }
    override fun init() {
        controls.clear(); port=null; progress=null
        top=(height/2-68).coerceAtLeast(52)
        val labels=listOf("saltlyrics.connection","saltlyrics.space","saltlyrics.animation")
        labels.forEachIndexed { i,key ->
            button(tr(key),width/2-154+i*105,27,100) {
                page=i; feedback=null; rebuildWidgets()
            }.active=page!=i
        }
        when(page) {
            0 -> connection()
            1 -> {
                toggle("saltlyrics.enabled",0,0,{LyricsConfig.enabled},{LyricsConfig.enabled=it})
                toggle("saltlyrics.face",0,1,{LyricsConfig.facePlayer},{LyricsConfig.facePlayer=it})
                slider("saltlyrics.scale",1,0,0.5,3.0,LyricsConfig.scale.toDouble(),0.05){LyricsConfig.scale=it.toFloat()}
                slider("saltlyrics.distance",1,1,3.0,40.0,LyricsConfig.distance.toDouble(),1.0){LyricsConfig.distance=it.toFloat()}
                slider("saltlyrics.spread",2,0,0.0,60.0,LyricsConfig.spread.toDouble(),1.0){LyricsConfig.spread=it.toFloat()}
                slider("saltlyrics.offset",2,1,-5000.0,5000.0,LyricsConfig.offsetMs.toDouble(),50.0){LyricsConfig.offsetMs=it.toLong()}
                toggle("saltlyrics.keepAway",3,0,{LyricsConfig.keepAway},{LyricsConfig.keepAway=it})
                toggle("saltlyrics.stay",3,1,{LyricsConfig.stayOnScreen},{LyricsConfig.stayOnScreen=it})
                toggle("saltlyrics.ground",4,0,{LyricsConfig.aboveGround},{LyricsConfig.aboveGround=it})
                toggle("saltlyrics.split",4,1,{LyricsConfig.splitLines},{LyricsConfig.splitLines=it})
                toggle("saltlyrics.translations",5,0,{LyricsConfig.showTranslations},{LyricsConfig.showTranslations=it})
            }
            2 -> {
                toggle("saltlyrics.wordPop",0,0,{LyricsConfig.wordPop},{LyricsConfig.wordPop=it})
                toggle("saltlyrics.wiggle",0,1,{LyricsConfig.wiggle},{LyricsConfig.wiggle=it})
                slider("saltlyrics.wiggleFps",1,0,4.0,24.0,LyricsConfig.wiggleFps.toDouble(),1.0){LyricsConfig.wiggleFps=it.toInt()}
                slider("saltlyrics.wiggleAmount",1,1,0.0,2.0,LyricsConfig.wiggleAmount.toDouble(),0.05){LyricsConfig.wiggleAmount=it.toFloat()}
                toggle("saltlyrics.glow",2,0,{LyricsConfig.glow},{LyricsConfig.glow=it})
                button(tr("saltlyrics.glowColor",tr("saltlyrics.color.${LyricsConfig.glowColor}")),width/2+4,top+48) {
                    LyricsConfig.glowColor=(LyricsConfig.glowColor+1)%4; rebuildWidgets()
                }
                slider("saltlyrics.glowStrength",3,0,0.0,3.0,LyricsConfig.glowStrength.toDouble(),0.05){LyricsConfig.glowStrength=it.toFloat()}
                slider("saltlyrics.glowRadius",3,1,0.0,1.0,LyricsConfig.glowRadius.toDouble(),0.05){LyricsConfig.glowRadius=it.toFloat()}
                val colors=listOf(0xF7E1D7,0xFFFFFF,0xF2A7C3,0x8ECFFF)
                val preset=colors.indexOf(LyricsConfig.textRgb)
                val colorName=if(preset>=0) tr("saltlyrics.text.$preset") else tr("saltlyrics.text.custom")
                button(tr("saltlyrics.textColor",colorName),width/2-154,top+96) {
                    LyricsConfig.textRgb=colors[(colors.indexOf(LyricsConfig.textRgb)+1)%colors.size]; rebuildWidgets()
                }
                button(tr("saltlyrics.rgb.open"),width/2+4,top+96) {
                    minecraft!!.setScreen(RgbColorScreen(this))
                }
            }
        }
        button(tr("gui.done"),width/2-100,height-27,200) { onClose() }
    }
    private fun connection() {
        maskedToken=SaltConfig.mask(SaltConfig.token())
        val x=width/2-154
        button(tr("saltlyrics.pasteToken"),x,top+29) {
            val clean=SaltConfig.sanitize(minecraft!!.keyboardHandler.clipboard)
            if(clean.isEmpty()) { feedback=tr("saltlyrics.emptyClipboard"); return@button }
            if(SaltConfig.saveToken(clean)) {
                SaltLyricsClient.session.restart(); maskedToken=SaltConfig.mask(clean); feedback=tr("saltlyrics.tokenSaved")
            } else feedback=tr("saltlyrics.saveFailed")
        }
        button(tr("saltlyrics.autoPair"),width/2+4,top+29) {
            if(SaltConfig.saveToken("")) {
                SaltLyricsClient.session.restart(); maskedToken=SaltConfig.mask(SaltConfig.token()); feedback=tr("saltlyrics.autoPairNote")
            } else feedback=tr("saltlyrics.saveFailed")
        }
        val field=EditBox(font,x+76,top+55,74,20,tr("saltlyrics.port"))
        field.setMaxLength(5)
        field.setFilter { it.isEmpty() || (it.length<=5 && it.all(Char::isDigit)) }
        field.value=draftPort
        field.setResponder { draftPort=it }
        addRenderableWidget(field); port=field
        button(tr("saltlyrics.reconnect"),width/2+4,top+55) {
            val value=field.value.toIntOrNull()
            if(value==null || value !in 1..65535) { feedback=tr("saltlyrics.invalidPort"); return@button }
            SaltConfig.port=value
            if(LyricsConfig.save()) {
                SaltConfig.invalidate(); SaltLyricsClient.session.restart(); feedback=tr("saltlyrics.reconnecting")
            } else feedback=tr("saltlyrics.saveFailed")
        }
        listOf("previous","play","pause","next").forEachIndexed { i,command ->
            controls+=button(tr("saltlyrics.$command"),x+i*79,top+82,71) {
                val session=SaltLyricsClient.session
                when(command) { "previous"->session.previous(); "play"->session.play(); "pause"->session.pause(); "next"->session.next() }
            }
        }
        val snapshot=SaltLyricsClient.session.snapshot()
        shownTrackKey=snapshot.track?.key
        progress=ProgressSlider(x,top+109,shownTrackKey,snapshot.track?.durationMs ?: 0L,snapshot.positionMs)
        addRenderableWidget(progress!!)
    }
    private inner class ProgressSlider(x: Int,y: Int,private val trackKey: String?,private val duration: Long,position: Long) :
        AbstractSliderButton(x,y,308,20,Component.empty(),if(duration>0) position.toDouble()/duration else 0.0) {
        private var pending=false
        init { updateMessage(); active=duration>0 }
        override fun updateMessage() {
            fun time(ms: Long): String { val seconds=ms.coerceAtLeast(0)/1000; return "%d:%02d".format(Locale.ROOT,seconds/60,seconds%60) }
            message=tr("saltlyrics.progress",time((value*duration).toLong()),time(duration))
        }
        override fun applyValue() { pending=true }
        fun sync(position: Long) {
            if(!pending) { value=if(duration>0) (position.toDouble()/duration).coerceIn(0.0,1.0) else 0.0; updateMessage() }
        }
        private fun commit() {
            if(!pending) return
            pending=false
            val session=SaltLyricsClient.session
            if(duration>0 && trackKey==session.snapshot().track?.key) session.seekTo((value*duration).toLong())
        }
        override fun mouseReleased(x: Double,y: Double,button: Int): Boolean {
            val result=super.mouseReleased(x,y,button)
            if(button==0) commit()
            return result
        }
        override fun keyPressed(key: Int,scan: Int,mods: Int): Boolean {
            val result=super.keyPressed(key,scan,mods)
            commit()
            return result
        }
    }
    override fun tick() {
        port?.tick()
        val s=SaltLyricsClient.session.snapshot()
        controls.forEach { it.active=s.link==SessionLink.READY && s.track!=null }
        if(page==0 && shownTrackKey!=s.track?.key) { rebuildWidgets(); return }
        progress?.sync(s.positionMs)
        progress?.active=s.link==SessionLink.READY && (s.track?.durationMs ?: 0L)>0
    }
    override fun render(g: GuiGraphics,mx: Int,my: Int,delta: Float) {
        renderBackground(g)
        g.drawCenteredString(font,title,width/2,8,0xFFFFFF)
        if(page==0) {
            val s=SaltLyricsClient.session
            g.drawString(font,tr("saltlyrics.status",tr("saltlyrics.status.${s.connection.name.lowercase(Locale.ROOT)}")),width/2-154,top,0xDDDDDD)
            val track=s.snapshot().track
            val title=track?.let { it.title+" — "+it.artistText } ?: tr("saltlyrics.noTrack").string
            g.drawString(font,font.plainSubstrByWidth(title,308),width/2-154,top+13,0xBBBBBB)
            g.drawString(font,tr("saltlyrics.port"),width/2-154,top+61,0xDDDDDD)
            val token=maskedToken
            val note=feedback ?: tr("saltlyrics.token",if(token.isEmpty()) tr("saltlyrics.auto").string else token)
            g.drawString(font,font.plainSubstrByWidth(note.string,308),width/2-154,top+136,0xCCCCCC)
        }
        super.render(g,mx,my,delta)
    }
    override fun onClose() {
        if(!LyricsConfig.save()) { feedback=tr("saltlyrics.saveFailed"); page=0; rebuildWidgets(); return }
        minecraft!!.setScreen(parent)
    }
    override fun isPauseScreen()=false
}
