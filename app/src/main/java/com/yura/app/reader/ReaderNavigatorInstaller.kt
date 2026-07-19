@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import android.os.Bundle
import android.view.ActionMode
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator

internal object ReaderNavigatorInstaller {
    fun install(
        activity: FragmentActivity,
        containerId: Int,
        data: ReaderState.Ready,
        tag: String,
        onPageChanged: (Int, Int, Locator) -> Unit,
        onCenterTap: () -> Unit,
        selectionActionModeCallback: ActionMode.Callback,
    ): EpubNavigatorFragment? {
        val fragmentFactory = data.navigatorFactory.createFragmentFactory(
            initialLocator = data.initialLocator,
            initialPreferences = data.initialPreferences,
            configuration = EpubNavigatorFragment.Configuration {
                servedAssets = listOf(ReaderFonts.SERVED_ASSETS_PATTERN)
                this.selectionActionModeCallback = selectionActionModeCallback
                addFontFamilyDeclaration(
                    fontFamily = ReaderFonts.LXGW_WEN_KAI,
                    alternates = listOf(FontFamily.SERIF),
                ) {
                    addFontFace {
                        addSource(ReaderFonts.LXGW_ASSET_PATH, preload = true)
                    }
                }
            },
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                    onPageChanged(pageIndex, totalPages, locator)
                }
            },
        )
        activity.supportFragmentManager.fragmentFactory = fragmentFactory
        activity.supportFragmentManager.commitNow {
            replace(containerId, EpubNavigatorFragment::class.java, Bundle(), tag)
        }
        return (activity.supportFragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment)?.also { navigator ->
            navigator.addInputListener(
                object : InputListener {
                    private val delegate = DirectionalNavigationAdapter(
                        navigator = navigator,
                        tapEdges = setOf(DirectionalNavigationAdapter.TapEdge.Horizontal),
                        animatedTransition = true,
                    )

                    override fun onTap(event: TapEvent): Boolean = delegate.onTap(event)
                },
            )
            navigator.addInputListener(
                object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val width = navigator.publicationView.width
                        val centerZone = width * 0.3f
                        if (kotlin.math.abs(event.point.x - width / 2f) < centerZone) onCenterTap()
                        return false
                    }
                },
            )
        }
    }
}
