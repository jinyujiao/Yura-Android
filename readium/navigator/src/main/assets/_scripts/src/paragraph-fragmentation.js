const FRAGMENT_ATTRIBUTE = "data-readium-paragraph-fragment";

class ParagraphFragmenter {
  constructor(window) {
    this.window = window;
    this.fragmentations = [];
    this.nextFragmentationId = 0;
    this.updateScheduled = false;
  }

  prepareForLayout() {
    for (const fragmentation of this.fragmentations) {
      const firstFragment = fragmentation.fragments[0];
      if (firstFragment.isConnected) {
        firstFragment.replaceWith(fragmentation.original);
      }
      for (const fragment of fragmentation.fragments.slice(1)) {
        fragment.remove();
      }
    }
    this.fragmentations = [];
    this.nextFragmentationId = 0;
  }

  scheduleUpdate() {
    if (this.updateScheduled) {
      return;
    }

    this.updateScheduled = true;
    this.window.requestAnimationFrame(() => {
      this.window.requestAnimationFrame(() => {
        this.updateScheduled = false;
        this.update();
      });
    });
  }

  update() {
    if (!this.isSupportedLayout()) {
      return;
    }

    const paragraphs = Array.from(this.window.document.body.querySelectorAll("p"));
    for (const paragraph of paragraphs) {
      this.fragment(paragraph);
    }
  }

  fragment(paragraph) {
    if (
      paragraph.hasAttribute(FRAGMENT_ATTRIBUTE) ||
      paragraph.childNodes.length !== 1 ||
      paragraph.firstChild?.nodeType !== Node.TEXT_NODE
    ) {
      return;
    }

    const textNode = paragraph.firstChild;
    const boundaries = this.columnBoundaries(textNode);
    if (boundaries.length === 0) {
      return;
    }

    const originalText = textNode.data;
    const fragmentationId = String(this.nextFragmentationId++);
    const fragments = splitAtBoundaries(originalText, boundaries).map(
      (text, index, all) => {
        const fragment = paragraph.cloneNode(false);
        fragment.setAttribute(FRAGMENT_ATTRIBUTE, fragmentationId);
        fragment.readiumOriginalParagraphText = originalText;
        fragment.readiumParagraphFragmentIndex = index;
        if (index > 0) {
          fragment.removeAttribute("id");
          fragment.style.setProperty("margin-top", "0", "important");
          fragment.style.setProperty("text-indent", "0", "important");
        }
        if (index < all.length - 1) {
          fragment.style.setProperty("margin-bottom", "0", "important");
          fragment.style.setProperty("break-after", "column", "important");
          fragment.style.setProperty(
            "-webkit-column-break-after",
            "always",
            "important"
          );
        }
        fragment.textContent = text;
        return fragment;
      }
    );

    paragraph.replaceWith(...fragments);
    this.fragmentations.push({ original: paragraph, fragments });
  }

  isSupportedLayout() {
    if (!this.window.readium?.isReflowable) {
      return false;
    }

    const rootStyle = this.window.getComputedStyle(
      this.window.document.documentElement
    );
    const view = this.window.document.documentElement.style
      .getPropertyValue("--USER__view")
      .trim();
    const legacyView = this.window.document.documentElement.style
      .getPropertyValue("--USER__scroll")
      .trim();
    const writingMode = rootStyle.getPropertyValue("writing-mode");
    return (
      view !== "readium-scroll-on" &&
      legacyView !== "readium-scroll-on" &&
      !writingMode.startsWith("vertical")
    );
  }

  columnBoundaries(textNode) {
    const viewportWidth = this.window.visualViewport?.width;
    if (!viewportWidth || textNode.length < 2) {
      return [];
    }

    const range = this.window.document.createRange();
    const columnAt = (offset) => {
      range.setStart(textNode, offset);
      range.setEnd(textNode, offset + 1);
      const rect = range.getBoundingClientRect();
      if (rect.width === 0 && rect.height === 0) {
        return undefined;
      }
      const documentX = rect.left + this.window.scrollX;
      return Math.floor((documentX + 1) / viewportWidth);
    };
    const measurableAtOrAfter = (offset, end) => {
      for (let candidate = offset; candidate <= end; candidate++) {
        const column = columnAt(candidate);
        if (column !== undefined) {
          return { offset: candidate, column };
        }
      }
      return undefined;
    };

    const first = measurableAtOrAfter(0, textNode.length - 1);
    if (!first) {
      range.detach();
      return [];
    }

    let lastOffset = textNode.length - 1;
    let lastColumn = columnAt(lastOffset);
    while (lastOffset > first.offset && lastColumn === undefined) {
      lastColumn = columnAt(--lastOffset);
    }
    if (lastColumn === undefined || lastColumn === first.column) {
      range.detach();
      return [];
    }

    const boundaries = [];
    let current = first;
    while (current.column < lastColumn && current.offset < lastOffset) {
      let low = current.offset + 1;
      let high = lastOffset;
      while (low < high) {
        const middle = Math.floor((low + high) / 2);
        const measured = measurableAtOrAfter(middle, high);
        if (!measured || measured.column > current.column) {
          high = measured?.offset ?? middle;
        } else {
          low = measured.offset + 1;
        }
      }

      const next = measurableAtOrAfter(low, lastOffset);
      if (!next || next.column <= current.column) {
        break;
      }
      boundaries.push(next.offset);
      current = next;
    }

    range.detach();
    return boundaries;
  }
}

export function splitAtBoundaries(text, boundaries) {
  const fragments = [];
  let start = 0;
  for (const boundary of boundaries) {
    if (boundary > start && boundary < text.length) {
      fragments.push(text.slice(start, boundary));
      start = boundary;
    }
  }
  fragments.push(text.slice(start));
  return fragments;
}

export const paragraphFragmenter = new ParagraphFragmenter(window);
