package com.yura.app.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import org.readium.r2.navigator.epub.EpubPreferences

internal object ReaderMediaStyleFixer {
    fun apply(root: View?, preferences: EpubPreferences) {
        val script = scriptFor(preferences)
        val webViews = mutableListOf<WebView>()
        collectWebViews(root, webViews)
        webViews.forEach { webView -> webView.evaluateJavascript(script, null) }
    }

    private fun scriptFor(preferences: EpubPreferences): String =
        if (preferences.publisherStyles != false) {
            """
            (function() {
                var style = document.getElementById('yura-media-indent-fix');
                if (style) style.remove();
                var blocks = document.querySelectorAll('.yura-media-block');
                for (var i = 0; i < blocks.length; i++) blocks[i].classList.remove('yura-media-block');
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                var style = document.getElementById('yura-media-indent-fix');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'yura-media-indent-fix';
                    document.head.appendChild(style);
                }
                style.textContent = [
                    'img, svg, video, canvas, picture { text-indent: 0 !important; margin-inline-start: 0 !important; }',
                    'p:has(img), p:has(svg), p:has(picture), div:has(> img), figure:has(img) { text-indent: 0 !important; }',
                    'body > img, body > picture, body > svg { display: block !important; margin-inline-start: auto !important; margin-inline-end: auto !important; }',
                    '.yura-media-block, .yura-media-block * { text-indent: 0 !important; }',
                    '.yura-media-block { text-align: center !important; }'
                ].join('\n');

                var oldBlocks = document.querySelectorAll('.yura-media-block');
                for (var i = 0; i < oldBlocks.length; i++) oldBlocks[i].classList.remove('yura-media-block');

                function hasMeaningfulText(element) {
                    for (var i = 0; i < element.childNodes.length; i++) {
                        var node = element.childNodes[i];
                        if (node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0) return true;
                    }
                    return false;
                }
                function isMediaOnly(element) {
                    if (!element) return false;
                    if (hasMeaningfulText(element)) return false;
                    var media = element.querySelectorAll('img, svg, video, canvas, picture');
                    if (media.length === 0) return false;
                    var text = (element.innerText || '').replace(/\s+/g, '').trim();
                    return text.length === 0;
                }
                var medias = document.querySelectorAll('img, svg, video, canvas, picture');
                for (var m = 0; m < medias.length; m++) {
                    var el = medias[m];
                    el.style.textIndent = '0';
                    el.style.marginInlineStart = '0';
                    var parent = el.parentElement;
                    var limit = 0;
                    while (parent && parent !== document.body && limit < 4) {
                        var tag = parent.tagName ? parent.tagName.toLowerCase() : '';
                        var compactText = (parent.innerText || '').replace(/\s+/g, '').trim();
                        var mostlyMedia = compactText.length <= 24 && parent.querySelector('img, svg, video, canvas, picture');
                        if ((tag === 'p' || tag === 'div' || tag === 'figure' || tag === 'section') && (isMediaOnly(parent) || mostlyMedia)) {
                            parent.classList.add('yura-media-block');
                            parent.style.textIndent = '0';
                        }
                        parent = parent.parentElement;
                        limit++;
                    }
                }
            })();
            """.trimIndent()
        }

    private fun collectWebViews(view: View?, result: MutableList<WebView>) {
        if (view is WebView) {
            result += view
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectWebViews(view.getChildAt(index), result)
        }
    }
}
