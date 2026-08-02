package com.yura.app.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

internal object ReaderPublisherColorFixer {
    fun apply(root: View?, preferences: EpubPreferences) {
        val script = scriptFor(preferences.theme == Theme.DARK)
        val webViews = mutableListOf<WebView>()
        collectWebViews(root, webViews)
        webViews.forEach { webView -> webView.evaluateJavascript(script, null) }
    }

    private fun scriptFor(darkTheme: Boolean): String =
        """
        (function() {
            var root = document.documentElement;
            if (!root) return;

            var styleId = 'yura-publisher-text-colors';
            var colorAttribute = 'data-yura-publisher-color';
            var readyAttribute = 'data-yura-publisher-colors-ready';

            function cleanup() {
                var oldStyle = document.getElementById(styleId);
                if (oldStyle) oldStyle.remove();
                var colored = document.querySelectorAll('[' + colorAttribute + ']');
                for (var i = 0; i < colored.length; i++) {
                    colored[i].removeAttribute(colorAttribute);
                }
                root.removeAttribute(readyAttribute);
            }

            if (!${darkTheme}) {
                cleanup();
                return;
            }
            if (!document.body || document.readyState === 'loading') return;

            var appearance = root.style.getPropertyValue('--USER__appearance');
            if (appearance.indexOf('readium-night-on') < 0) return;

            var stylesheetLinks = document.querySelectorAll('link[rel~="stylesheet"]');
            for (var linkIndex = 0; linkIndex < stylesheetLinks.length; linkIndex++) {
                if (!stylesheetLinks[linkIndex].sheet) return;
            }
            if (root.getAttribute(readyAttribute) === '1') return;

            cleanup();
            var candidates = new Map();

            function addCandidate(element, properties) {
                var existing = candidates.get(element) || { color: false, background: false, border: false };
                existing.color = existing.color || properties.color;
                existing.background = existing.background || properties.background;
                existing.border = existing.border || properties.border;
                candidates.set(element, existing);
            }

            function addMatches(selector, properties) {
                if (!selector) return;
                try {
                    var matches = document.querySelectorAll(selector);
                    for (var matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                        addCandidate(matches[matchIndex], properties);
                    }
                } catch (_) {
                }
            }

            function collectColorRules(ruleList) {
                if (!ruleList) return;
                for (var ruleIndex = 0; ruleIndex < ruleList.length; ruleIndex++) {
                    var rule = ruleList[ruleIndex];
                    var selector = rule.selectorText || '';
                    var readiumManaged = selector.indexOf('readium-') >= 0 || selector.indexOf('--USER__') >= 0;
                    if (!readiumManaged && rule.style) {
                        var properties = {
                            color: !!rule.style.getPropertyValue('color'),
                            background: !!(rule.style.getPropertyValue('background-color') || rule.style.getPropertyValue('background')),
                            border: !!(
                                rule.style.getPropertyValue('border-color') ||
                                rule.style.getPropertyValue('border') ||
                                rule.style.getPropertyValue('border-top') ||
                                rule.style.getPropertyValue('border-right') ||
                                rule.style.getPropertyValue('border-bottom') ||
                                rule.style.getPropertyValue('border-left')
                            )
                        };
                        if (properties.color || properties.background || properties.border) {
                            addMatches(rule.selectorText, properties);
                        }
                    }
                    try {
                        if (rule.cssRules) collectColorRules(rule.cssRules);
                    } catch (_) {
                    }
                }
            }

            var inlineCandidates = document.querySelectorAll('[style], [color], [bgcolor], body[text]');
            for (var inlineIndex = 0; inlineIndex < inlineCandidates.length; inlineIndex++) {
                var inlineElement = inlineCandidates[inlineIndex];
                var inlineStyle = inlineElement.style;
                var inlineProperties = {
                    color: !!(
                        (inlineStyle && inlineStyle.getPropertyValue('color')) ||
                        inlineElement.hasAttribute('color') ||
                        inlineElement.hasAttribute('text')
                    ),
                    background: !!(
                        (inlineStyle && (inlineStyle.getPropertyValue('background-color') || inlineStyle.getPropertyValue('background'))) ||
                        inlineElement.hasAttribute('bgcolor')
                    ),
                    border: !!(
                        inlineStyle && (
                            inlineStyle.getPropertyValue('border-color') ||
                            inlineStyle.getPropertyValue('border') ||
                            inlineStyle.getPropertyValue('border-top') ||
                            inlineStyle.getPropertyValue('border-right') ||
                            inlineStyle.getPropertyValue('border-bottom') ||
                            inlineStyle.getPropertyValue('border-left')
                        )
                    )
                };
                if (inlineProperties.color || inlineProperties.background || inlineProperties.border) {
                    addCandidate(inlineElement, inlineProperties);
                }
            }
            for (var sheetIndex = 0; sheetIndex < document.styleSheets.length; sheetIndex++) {
                try {
                    collectColorRules(document.styleSheets[sheetIndex].cssRules);
                } catch (_) {
                }
            }

            if (candidates.size === 0) {
                root.setAttribute(readyAttribute, '1');
                return;
            }

            var appearancePriority = root.style.getPropertyPriority('--USER__appearance');
            root.style.removeProperty('--USER__appearance');
            var rules = [];

            function parseColor(value) {
                var match = value && value.match(/rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,\/]\s*([\d.]+))?\s*\)/i);
                if (!match) return null;
                return {
                    red: Math.max(0, Math.min(255, Number(match[1]))),
                    green: Math.max(0, Math.min(255, Number(match[2]))),
                    blue: Math.max(0, Math.min(255, Number(match[3]))),
                    alpha: match[4] == null ? 1 : Math.max(0, Math.min(1, Number(match[4])))
                };
            }

            function channelLuminance(channel) {
                var value = channel / 255;
                return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
            }

            function contrast(color) {
                var luminance = 0.2126 * channelLuminance(color.red * color.alpha) +
                    0.7152 * channelLuminance(color.green * color.alpha) +
                    0.0722 * channelLuminance(color.blue * color.alpha);
                return (luminance + 0.05) / 0.05;
            }

            function rgbToHsl(color) {
                var red = color.red / 255;
                var green = color.green / 255;
                var blue = color.blue / 255;
                var maximum = Math.max(red, green, blue);
                var minimum = Math.min(red, green, blue);
                var lightness = (maximum + minimum) / 2;
                var hue = 0;
                var saturation = 0;
                if (maximum !== minimum) {
                    var difference = maximum - minimum;
                    saturation = lightness > 0.5 ? difference / (2 - maximum - minimum) : difference / (maximum + minimum);
                    if (maximum === red) hue = ((green - blue) / difference + (green < blue ? 6 : 0)) / 6;
                    else if (maximum === green) hue = ((blue - red) / difference + 2) / 6;
                    else hue = ((red - green) / difference + 4) / 6;
                }
                return { hue: hue, saturation: saturation, lightness: lightness };
            }

            function hueToRgb(p, q, t) {
                if (t < 0) t += 1;
                if (t > 1) t -= 1;
                if (t < 1 / 6) return p + (q - p) * 6 * t;
                if (t < 1 / 2) return q;
                if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
                return p;
            }

            function hslToRgb(hsl, alpha) {
                var red;
                var green;
                var blue;
                if (hsl.saturation === 0) {
                    red = green = blue = hsl.lightness;
                } else {
                    var q = hsl.lightness < 0.5
                        ? hsl.lightness * (1 + hsl.saturation)
                        : hsl.lightness + hsl.saturation - hsl.lightness * hsl.saturation;
                    var p = 2 * hsl.lightness - q;
                    red = hueToRgb(p, q, hsl.hue + 1 / 3);
                    green = hueToRgb(p, q, hsl.hue);
                    blue = hueToRgb(p, q, hsl.hue - 1 / 3);
                }
                return { red: red * 255, green: green * 255, blue: blue * 255, alpha: alpha };
            }

            function accessibleColor(color) {
                var minimumContrast = 4.5;
                if (contrast(color) >= minimumContrast) return color;
                var hsl = rgbToHsl(color);
                var low = hsl.lightness;
                var high = 1;
                var candidate = color;
                for (var i = 0; i < 12; i++) {
                    hsl.lightness = (low + high) / 2;
                    candidate = hslToRgb(hsl, color.alpha);
                    if (contrast(candidate) >= minimumContrast) high = hsl.lightness;
                    else low = hsl.lightness;
                }
                if (contrast(candidate) < minimumContrast) {
                    candidate.alpha = 1;
                }
                return candidate;
            }

            function sameColor(first, second) {
                if (!first || !second) return true;
                return Math.abs(first.red - second.red) < 1 &&
                    Math.abs(first.green - second.green) < 1 &&
                    Math.abs(first.blue - second.blue) < 1 &&
                    Math.abs(first.alpha - second.alpha) < 0.01;
            }

            function isDefaultDarkText(color) {
                return color && color.alpha >= 0.95 &&
                    color.red <= 24 && color.green <= 24 && color.blue <= 24;
            }

            function cssColor(color) {
                return 'rgba(' + Math.round(color.red) + ', ' + Math.round(color.green) + ', ' +
                    Math.round(color.blue) + ', ' + color.alpha.toFixed(3) + ')';
            }

            try {
                var computedStyles = new WeakMap();

                function computedStyle(element) {
                    if (!element) return null;
                    if (computedStyles.has(element)) return computedStyles.get(element);
                    var value = window.getComputedStyle(element);
                    computedStyles.set(element, value);
                    return value;
                }

                function computedColor(element, property) {
                    var style = computedStyle(element);
                    return style ? parseColor(style[property]) : null;
                }

                var entries = Array.from(candidates.entries());
                for (var index = 0; index < entries.length; index++) {
                    var element = entries[index][0];
                    var properties = entries[index][1];
                    var tagName = element.tagName ? element.tagName.toLowerCase() : '';
                    if (tagName === 'script' || tagName === 'style' || tagName === 'link' || tagName === 'meta' || tagName === 'noscript') continue;

                    var declarations = [];
                    if (properties.color) {
                        var color = computedColor(element, 'color');
                        var parent = element.parentElement;
                        var parentColor = element === root
                            ? { red: 18, green: 18, blue: 18, alpha: 1 }
                            : computedColor(parent, 'color');
                        if (color && !sameColor(color, parentColor) && !isDefaultDarkText(color)) {
                            declarations.push('color: ' + cssColor(accessibleColor(color)) + ' !important;');
                        }
                    }
                    if (properties.background && element !== root && tagName !== 'body') {
                        var backgroundColor = computedColor(element, 'backgroundColor');
                        if (backgroundColor && backgroundColor.alpha > 0.01) {
                            declarations.push('background-color: ' + cssColor(backgroundColor) + ' !important;');
                        }
                    }
                    if (properties.border) {
                        var borderSides = ['Top', 'Right', 'Bottom', 'Left'];
                        for (var sideIndex = 0; sideIndex < borderSides.length; sideIndex++) {
                            var side = borderSides[sideIndex];
                            var borderColor = computedColor(element, 'border' + side + 'Color');
                            if (borderColor && borderColor.alpha > 0.01) {
                                declarations.push('border-' + side.toLowerCase() + '-color: ' + cssColor(borderColor) + ' !important;');
                            }
                        }
                    }
                    if (declarations.length === 0) continue;

                    var colorId = rules.length.toString();
                    element.setAttribute(colorAttribute, colorId);
                    rules.push(':root[style*="readium-night-on"][' + colorAttribute + '="' + colorId + '"], ' +
                        ':root[style*="readium-night-on"] [' + colorAttribute + '="' + colorId + '"] {' +
                        declarations.join(' ') + ' }');
                }
            } finally {
                root.style.setProperty('--USER__appearance', appearance, appearancePriority || 'important');
            }

            if (rules.length > 0) {
                var style = document.createElement('style');
                style.id = styleId;
                style.textContent = rules.join('\n');
                (document.head || root).appendChild(style);
            }
            root.setAttribute(readyAttribute, '1');
        })();
        """.trimIndent()

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
