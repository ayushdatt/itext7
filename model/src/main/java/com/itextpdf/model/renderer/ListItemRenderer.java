package com.itextpdf.model.renderer;

import com.itextpdf.model.element.ListItem;
import com.itextpdf.model.element.Paragraph;

public class ListItemRenderer extends BlockRenderer {
    public ListItemRenderer(ListItem modelElement) {
        super(modelElement);
    }

    public void addSymbolRenderer(IRenderer renderer) {
        if (childRenderers.size() == 0) {
            super.addChild(new Paragraph().setMarginTop(0).setMarginBottom(0).createRendererSubTree());
            ((ParagraphRenderer)childRenderers.get(0)).addChildFront(renderer);
        } else if (childRenderers.get(0) instanceof ParagraphRenderer) {
            ((ParagraphRenderer) childRenderers.get(0)).addChildFront(renderer);
        } else {
            IRenderer newPairRenderer = new Paragraph().setMarginTop(0).setMarginBottom(0).createRendererSubTree().setParent(this);
            newPairRenderer.addChild(renderer);
            newPairRenderer.addChild(childRenderers.get(0));
            childRenderers.set(0, newPairRenderer);
        }
    }
}