package org.vpinball.app

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives a secondary display (e.g. the AYN Thor bottom screen) from the VPX player.
 *
 * SDL on Android supports a single window, so VPX normally embeds the backglass and score view inside the playfield.
 * This presenter opens an Android Presentation on the secondary display, and hands its Surface to the native player
 * as an "external surface" for one ancillary window. The native renderer then creates a second BGFX swapchain on it.
 *
 * Which window is routed is read from VPinballX.ini:
 *  - [Backglass] BackglassOutput = 1 (Floating)  -> backglass goes to the second screen
 *  - else [ScoreView] ScoreViewOutput = 1 (Floating) -> score view (DMD) goes to the second screen
 *  - else nothing is routed and the player behaves as stock.
 */
class SecondScreenPresenter(private val context: Context) {
    private var presentation: Presentation? = null
    private var windowId = 0
    private val ready = CompletableDeferred<Boolean>()

    /** Set when the surface dies while a table runs. The swapchain then points at a dead surface, so the table must stop. */
    var onSurfaceLost: (() -> Unit)? = null

    fun start() {
        windowId = selectWindowId()
        if (windowId == 0) {
            ready.complete(false)
            return
        }
        val display = findSecondaryDisplay()
        if (display == null) {
            VPinballManager.log(VPinballLogLevel.WARN, "Second screen: no secondary display found, keeping stock layout")
            ready.complete(false)
            return
        }
        VPinballManager.log(VPinballLogLevel.INFO, "Second screen: routing window #$windowId to display ${display.displayId} (${display.name})")
        presentation = SurfacePresentation(context, display).also { it.show() }
    }

    /** Wait until the surface is handed to the native side, or give up after the timeout. */
    suspend fun awaitReady(timeoutMs: Long = 3000): Boolean = withTimeoutOrNull(timeoutMs) { ready.await() } ?: false

    fun stop() {
        presentation?.dismiss()
        presentation = null
        if (windowId != 0) {
            VPinballManager.vpinballJNI.VPinballSetExternalSurface(windowId, null, 0, 0, 0f)
        }
    }

    private fun selectWindowId(): Int {
        val jni = VPinballManager.vpinballJNI
        return when {
            jni.VPinballLoadValueInt("Backglass", "BackglassOutput", 0) == OUTPUT_FLOATING -> WINDOW_BACKGLASS
            jni.VPinballLoadValueInt("ScoreView", "ScoreViewOutput", 0) == OUTPUT_FLOATING -> WINDOW_SCOREVIEW
            else -> 0
        }
    }

    private fun findSecondaryDisplay(): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // Prefer displays flagged for presentations; fall back to any non-default display
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private inner class SurfacePresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val surfaceView = SurfaceView(context)
            surfaceView.holder.addCallback(
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {}

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        // Hand over the surface only once: the native swapchain is created at player start and is not resized
                        if (ready.isCompleted) return
                        VPinballManager.vpinballJNI.VPinballSetExternalSurface(windowId, holder.surface, width, height, display.refreshRate)
                        ready.complete(true)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        VPinballManager.log(VPinballLogLevel.WARN, "Second screen: surface destroyed")
                        onSurfaceLost?.invoke()
                    }
                }
            )
            setContentView(surfaceView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    companion object {
        private const val OUTPUT_FLOATING = 1 // RenderOutput::OM_WINDOW
        private const val WINDOW_BACKGLASS = 1 // VPXWINDOW_Backglass
        private const val WINDOW_SCOREVIEW = 2 // VPXWINDOW_ScoreView
    }
}
